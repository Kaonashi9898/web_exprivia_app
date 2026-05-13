import { ChangeDetectorRef, Component, ElementRef, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable, of, Subscription, map } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Edificio, Piano, PlanimetriaLayout, PlanimetriaResponse, Postazione, Prenotazione, Sede, Stanza, StatoPostazione } from '../../core/app.models';
import { apiErrorMessage, apiErrorStatus } from '../../core/api-error.utils';
import { AuthService } from '../../core/auth.service';
import {
  BOOKING_DAY_END,
  BOOKING_DAY_START,
  BOOKING_END_OPTIONS,
  BOOKING_START_OPTIONS,
  ceilBookingStartOption,
  isWithinBookingWindow,
  nextBookingTimeOption,
} from '../../core/booking-time.utils';
import { isWeekendIsoDate, nextBookableIsoDate } from '../../core/date.utils';
import { OPERATIONAL_BOOKING_ROLES } from '../../core/role-access';
import { roomZoom, roomZoomStyle, RoomZoomInput } from '../../core/plan-zoom.utils';

type LayoutRoom = NonNullable<PlanimetriaLayout['rooms']>[number];
type LayoutMeeting = NonNullable<PlanimetriaLayout['meetings']>[number];
type DisplayRoom = LayoutRoom | LayoutMeeting;
type PositionedRoom = DisplayRoom & { position: { xPct: number; yPct: number } };
type LayoutStation = NonNullable<PlanimetriaLayout['stations']>[number];
type PositionedStation = LayoutStation & { position: { xPct: number; yPct: number } };

@Component({
  selector: 'app-prenotazioni',
  imports: [FormsModule, NgStyle],
  templateUrl: './prenotazioni.html',
  styleUrl: './prenotazioni.css',
})
export class PrenotazioniComponent implements OnInit, OnDestroy {
  @ViewChild('planPreview') private planPreview?: ElementRef<HTMLElement>;
  @ViewChild('planStage') private planStage?: ElementRef<HTMLElement>;

  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private sediSubscription: Subscription | null = null;
  private edificiSubscription: Subscription | null = null;
  private pianiSubscription: Subscription | null = null;
  private planimetriaSubscription: Subscription | null = null;
  private imageSubscription: Subscription | null = null;
  private layoutSubscription: Subscription | null = null;
  private seatsSubscription: Subscription | null = null;
  private userGroupsSubscription: Subscription | null = null;
  private bookingsSubscription: Subscription | null = null;
  private createBookingSubscription: Subscription | null = null;
  private currentPlanRequestId = 0;
  private currentBookingsRequestId = 0;

  sedi: Sede[] = [];
  edifici: Edificio[] = [];
  piani: Piano[] = [];
  stanze: Stanza[] = [];
  meetingRooms: Stanza[] = [];
  postazioni: Postazione[] = [];
  bookings: Prenotazione[] = [];
  seatGroupIdsBySeatId: Record<number, number[]> = {};
  currentUserGroupIds = new Set<number>();
  selectedSedeId: number | null = null;
  selectedEdificioId: number | null = null;
  selectedPianoId: number | null = null;
  selectedRoomId: string | null = null;
  selectedStation: LayoutStation | null = null;
  selectedPostazione: Postazione | null = null;
  selectedMeetingRoom: Stanza | null = null;
  suggestedStartTime: string | null = null;

  planimetria: PlanimetriaResponse | null = null;
  layout: PlanimetriaLayout | null = null;
  imageSrc = '';
  unavailableMessage = '';
  message = '';
  error = '';
  availabilityError = '';
  sediLoading = false;
  bookingsLoading = false;
  bookingsLoaded = false;

  readonly minBookingDate = nextBookableIsoDate();
  readonly bookingStartOptions = BOOKING_START_OPTIONS;
  readonly bookingEndOptions = BOOKING_END_OPTIONS;
  bookingDate = this.minBookingDate;
  startTime = BOOKING_DAY_START;
  endTime = BOOKING_DAY_END;
  lockedBookingDate: string | null = null;

  ngOnInit(): void {
    this.loadCurrentUserGroups();
    this.sediLoading = true;
    this.sediSubscription?.unsubscribe();
    this.sediSubscription = this.api.listSedi().subscribe({
      next: (sedi) => {
        this.sedi = sedi;
        this.sediLoading = false;
        this.refreshView();
      },
      error: (err) => {
        this.sediLoading = false;
        this.error = apiErrorMessage(err, 'Impossibile caricare le sedi.');
        this.refreshView();
      },
    });
  }

  ngOnDestroy(): void {
    this.sediSubscription?.unsubscribe();
    this.edificiSubscription?.unsubscribe();
    this.pianiSubscription?.unsubscribe();
    this.planimetriaSubscription?.unsubscribe();
    this.imageSubscription?.unsubscribe();
    this.layoutSubscription?.unsubscribe();
    this.seatsSubscription?.unsubscribe();
    this.userGroupsSubscription?.unsubscribe();
    this.bookingsSubscription?.unsubscribe();
    this.createBookingSubscription?.unsubscribe();
    this.revokeImageUrl();
  }

  onSedeChange(): void {
    this.resetPlan();
    this.edifici = [];
    this.piani = [];
    this.selectedEdificioId = null;
    this.selectedPianoId = null;

    if (!this.selectedSedeId) {
      return;
    }

    this.edificiSubscription?.unsubscribe();
    this.edificiSubscription = this.api.listEdifici(this.selectedSedeId).subscribe({
        next: (edifici) => {
          this.edifici = edifici;
          if (!edifici.length) {
            this.unavailableMessage = 'Per questa sede non sono ancora configurati edifici.';
          }
          this.refreshView();
        },
        error: (err) => {
          this.error = apiErrorMessage(err, 'Impossibile caricare la sede.');
          this.refreshView();
        },
      });
  }

  onEdificioChange(): void {
    this.resetPlan();
    this.piani = [];
    this.selectedPianoId = null;

    if (!this.selectedEdificioId) {
      return;
    }

    this.pianiSubscription?.unsubscribe();
    this.pianiSubscription = this.api.listPiani(this.selectedEdificioId).subscribe({
      next: (piani) => {
        this.piani = piani;
        if (!this.piani.length) {
          this.unavailableMessage = 'Per questo edificio non sono ancora stati creati piani.';
        }
        this.refreshView();
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Impossibile caricare i piani.');
        this.refreshView();
      },
    });
  }

  onPianoChange(): void {
    this.resetPlan();
    if (this.selectedPianoId) {
      this.loadPlan(this.selectedPianoId);
    }
  }

  selectRoom(room: DisplayRoom): void {
    if (this.roomIsRestricted(room)) {
      return;
    }

    this.selectedRoomId = room.id;
    this.selectedStation = null;
    this.selectedPostazione = null;
    this.selectedMeetingRoom = this.roomIsMeeting(room) ? this.findStanzaForRoom(room) : null;
    this.resetPlanPreviewScroll();
    this.suggestedStartTime = this.findSuggestedStartTimeForSelectedResource();
    this.normalizeSelectableTimesForCurrentResource();
    this.updateOverlapMessage();
    this.refreshView();
  }

  resetZoom(): void {
    this.selectedRoomId = null;
    this.selectedStation = null;
    this.selectedPostazione = null;
    this.selectedMeetingRoom = null;
    this.resetPlanPreviewScroll();
    this.refreshView();
  }

  onPlanImageLoad(): void {
    this.refreshView();
  }

  selectStation(station: LayoutStation): void {
    const postazione = this.findPostazione(station);
    if (!postazione || postazione.stato !== 'DISPONIBILE' || !this.postazioneIsAccessible(postazione)) {
      return;
    }

    this.selectedStation = station;
    this.selectedRoomId = station.roomId ?? this.selectedRoomId;
    this.selectedPostazione = postazione;
    this.selectedMeetingRoom = null;
    if (!this.bookingsAreReady()) {
      this.suggestedStartTime = null;
      this.refreshView();
      return;
    }
    this.suggestedStartTime = this.findSuggestedStartTime(station);
    this.normalizeSelectableTimesForCurrentResource();
    this.updateOverlapMessage();
    this.refreshView();
  }

  createBooking(): void {
    if (!this.selectedPostazione && !this.selectedMeetingRoom) {
      return;
    }
    if (this.bookingControlsLocked()) {
      return;
    }

    const dateError = this.validateBookingDateSelection();
    if (dateError) {
      this.error = dateError;
      this.message = '';
      this.refreshView();
      return;
    }

    const timeError = this.validateBookingTimeSelection();
    if (timeError) {
      this.error = timeError;
      this.message = '';
      this.refreshView();
      return;
    }

    if (this.selectedUserHasOverlap()) {
      this.error = `Hai gia una prenotazione nella fascia selezionata. Prima disponibilita suggerita dalle ${this.suggestedStartTime ?? '09:00'}.`;
      this.message = '';
      this.refreshView();
      return;
    }

    this.clearMessages();
    this.createBookingSubscription?.unsubscribe();
    const request = {
      postazioneId: this.selectedPostazione?.id ?? null,
      meetingRoomStanzaId: this.selectedMeetingRoom?.id ?? null,
      dataPrenotazione: this.bookingDate,
      oraInizio: this.startTime,
      oraFine: this.endTime,
    };
    const booking$ = this.selectedMeetingRoom
      ? this.api.createMeetingRoomBooking({
        meetingRoomStanzaId: request.meetingRoomStanzaId,
        dataPrenotazione: this.bookingDate,
        oraInizio: this.startTime,
        oraFine: this.endTime,
      })
      : this.api.createBooking({
        postazioneId: request.postazioneId,
        dataPrenotazione: this.bookingDate,
        oraInizio: this.startTime,
        oraFine: this.endTime,
      });
    this.createBookingSubscription = booking$
      .subscribe({
        next: () => {
          this.lockedBookingDate = this.bookingDate;
          this.message = 'Prenotazione confermata.';
          this.error = '';
          this.suggestedStartTime = null;
          this.loadBookingsForCurrentPlan();
          this.refreshView();
        },
        error: (err) => {
          this.error = apiErrorMessage(err, 'Prenotazione non riuscita.');
          this.refreshView();
        },
      });
  }

  stationsForSelectedRoom(): LayoutStation[] {
    const stations = this.layout?.stations ?? [];
    if (!this.selectedRoomId || this.selectedMeetingRoom) {
      return [];
    }
    return stations.filter((station) => station.roomId === this.selectedRoomId);
  }

  private stationsForRoom(roomId: string): LayoutStation[] {
    return (this.layout?.stations ?? []).filter((station) => station.roomId === roomId);
  }

  private resetPlanPreviewScroll(): void {
    const preview = this.planPreview?.nativeElement;
    if (!preview) {
      return;
    }
    preview.scrollLeft = 0;
    preview.scrollTop = 0;
  }

  private loadCurrentUserGroups(): void {
    this.userGroupsSubscription?.unsubscribe();
    this.userGroupsSubscription = this.api.listMyGroups().subscribe({
      next: (groups) => {
        this.currentUserGroupIds = new Set(groups.map((group) => group.id));
        this.refreshView();
      },
      error: () => {
        this.currentUserGroupIds = new Set();
        this.refreshView();
      },
    });
  }

  selectedRoomLabel(): string {
    return this.roomsForDisplay().find((room) => room.id === this.selectedRoomId)?.label ?? 'Seleziona una stanza';
  }

  roomsForDisplay(): DisplayRoom[] {
    const byId = new Map<string, DisplayRoom>();
    for (const room of this.layout?.rooms ?? []) {
      byId.set(room.id, room);
    }
    for (const meeting of this.layout?.meetings ?? []) {
      byId.set(meeting.id, meeting);
    }
    return Array.from(byId.values());
  }

  visibleRooms(): PositionedRoom[] {
    return this.roomsForDisplay().filter((room): room is PositionedRoom => !!room.position);
  }

  visibleStations(): PositionedStation[] {
    return this.stationsForSelectedRoom().filter((station): station is PositionedStation => !!station.position);
  }

  stationIsBooked(station: LayoutStation): boolean {
    if (!this.bookingsAreReady()) {
      return false;
    }
    return this.stationHasOverlap(station);
  }

  stationIsAvailable(station: LayoutStation): boolean {
    const postazione = this.findPostazione(station);
    return !!postazione
      && postazione.stato === 'DISPONIBILE'
      && this.postazioneIsAccessible(postazione)
      && this.bookingsAreReady()
      && this.hasValidBookingWindow()
      && !this.stationHasOverlap(station);
  }

  stationIsUnavailable(station: LayoutStation): boolean {
    const postazione = this.findPostazione(station);
    return !postazione
      || postazione.stato !== 'DISPONIBILE'
      || !this.postazioneIsAccessible(postazione)
      || !this.bookingsAreReady();
  }

  stationIsRestricted(station: LayoutStation): boolean {
    const postazione = this.findPostazione(station);
    return !!postazione && !this.postazioneIsAccessible(postazione);
  }

  stationIsPartiallyBooked(station: LayoutStation): boolean {
    if (!this.bookingsAreReady()) {
      return false;
    }
    const postazione = this.findPostazione(station);
    return !!postazione && postazione.stato === 'DISPONIBILE' && this.stationHasBookings(station);
  }

  roomIsMeeting(room: DisplayRoom): boolean {
    return (this.layout?.meetings ?? []).some((meeting) => meeting.id === room.id);
  }

  roomIsRestricted(room: DisplayRoom): boolean {
    if (this.roomIsMeeting(room)) {
      const stanza = this.findStanzaForRoom(room);
      return !stanza || !this.meetingRoomIsBookable(stanza);
    }

    const roomStations = this.stationsForRoom(room.id);
    if (!roomStations.length) {
      return true;
    }

    return !roomStations.some((station) => {
      const postazione = this.findPostazione(station);
      return !!postazione && this.postazioneIsAccessible(postazione);
    });
  }

  roomPinTooltip(room: DisplayRoom): string {
    if (!this.roomIsMeeting(room)) {
      if (this.roomIsRestricted(room)) {
        return 'Nessuna postazione accessibile per i tuoi gruppi';
      }
      return room.label;
    }

    const stanza = this.findStanzaForRoom(room);
    if (!stanza) {
      return 'Sala riunioni non sincronizzata';
    }
    if (!this.meetingRoomIsBookable(stanza)) {
      return `Sala riunioni non prenotabile: ${this.statoLabel(stanza.stato ?? 'DISPONIBILE')}`;
    }

    const dateError = this.validateBookingDateSelection(false);
    if (dateError) {
      return dateError;
    }

    const timeError = this.validateBookingTimeSelection();
    if (timeError) {
      return timeError;
    }

    if (!this.bookingsAreReady()) {
      return this.availabilityStatusMessage();
    }

    const overlappingBooking = this.bookingsForMeetingRoom(stanza)
      .find((booking) => this.bookingOverlapsSelectedWindow(booking));
    if (overlappingBooking) {
      return `Sala occupata nella fascia selezionata dalle ${overlappingBooking.oraInizio} alle ${overlappingBooking.oraFine}`;
    }

    return stanza.nome;
  }

  private postazioneIsAccessible(postazione: Postazione): boolean {
    const groupIds = this.seatGroupIdsBySeatId[postazione.id] ?? [];
    if (!groupIds.length) {
      return true;
    }

    return groupIds.some((groupId) => this.currentUserGroupIds.has(groupId));
  }

  meetingRoomIsBooked(room: DisplayRoom): boolean {
    const stanza = this.findStanzaForRoom(room);
    return !!stanza && this.bookingsAreReady() && this.meetingRoomHasOverlap(stanza);
  }

  meetingRoomIsPartiallyBooked(room: DisplayRoom): boolean {
    const stanza = this.findStanzaForRoom(room);
    return !!stanza && this.bookingsAreReady() && this.meetingRoomHasBookings(stanza);
  }

  bookingForStation(station: LayoutStation): Prenotazione | null {
    return this.bookingsForStation(station).find((booking) => this.bookingOverlapsSelectedWindow(booking)) ?? null;
  }

  bookingsForStation(station: LayoutStation): Prenotazione[] {
    const postazione = this.findPostazione(station);
    if (!postazione) {
      return [];
    }

    return this.bookings.filter((booking) =>
      booking.stato === 'CONFERMATA' && booking.postazioneId === postazione.id,
    );
  }

  stationTooltip(station: LayoutStation): string {
    const dateError = this.validateBookingDateSelection(false);
    if (dateError) {
      return dateError;
    }

    const timeError = this.validateBookingTimeSelection();
    if (timeError) {
      return timeError;
    }

    if (!this.bookingsAreReady()) {
      return this.availabilityStatusMessage();
    }

    const bookings = this.bookingsForStation(station);
    const overlappingBooking = bookings.find((booking) => this.bookingOverlapsSelectedWindow(booking));
    if (overlappingBooking) {
      return `Occupata nella fascia selezionata dalle ${overlappingBooking.oraInizio} alle ${overlappingBooking.oraFine}`;
    }

    if (bookings.length) {
      const fasce = bookings
        .map((booking) => `${booking.oraInizio}-${booking.oraFine}`)
        .join(', ');
      return `Occupata parzialmente nelle fasce ${fasce}. Libera per l'orario selezionato.`;
    }

    const postazione = this.findPostazione(station);
    if (!postazione) {
      return 'Postazione non sincronizzata';
    }
    if (postazione.stato !== 'DISPONIBILE') {
      return 'Postazione non prenotabile';
    }
    return station.label;
  }

  selectedWindowSummary(): string {
    return `${this.formatBookingDate(this.bookingDate)} dalle ${this.startTime} alle ${this.endTime}`;
  }

  private formatBookingDate(value: string): string {
    return new Date(`${value}T00:00:00`).toLocaleDateString('it-IT', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  }

  selectedStationCanBeBooked(): boolean {
    return !!this.selectedPostazione
      && this.bookingsAreReady()
      && this.hasValidBookingWindow()
      && !this.bookingControlsLocked()
      && !this.selectedStationHasOverlap();
  }

  selectedResourceCanBeBooked(): boolean {
    if (this.selectedMeetingRoom) {
      return this.bookingsAreReady()
        && this.hasValidBookingWindow()
        && !this.bookingControlsLocked()
        && this.meetingRoomIsBookable(this.selectedMeetingRoom)
        && !this.selectedMeetingRoomHasOverlap();
    }

    return this.selectedStationCanBeBooked();
  }

  selectedResourceLabel(): string {
    if (this.selectedMeetingRoom) {
      return this.selectedMeetingRoom.nome;
    }
    return this.selectedPostazione?.codice || this.selectedStation?.label || 'Nessuna';
  }

  selectedResourceTypeLabel(): string {
    return this.selectedMeetingRoom ? 'Sala selezionata' : 'Postazione selezionata';
  }

  selectedStationBookingsSummary(): string[] {
    if (!this.selectedStation || !this.bookingsAreReady()) {
      return [];
    }
    return this.bookingsForStation(this.selectedStation)
      .map((booking) => `${booking.oraInizio}-${booking.oraFine}`)
      .sort();
  }

  selectedStationIsPartiallyOccupied(): boolean {
    return this.selectedStationBookingsSummary().length > 0;
  }

  selectedResourceBookingsSummary(): string[] {
    if (this.selectedMeetingRoom && this.bookingsAreReady()) {
      return this.bookingsForMeetingRoom(this.selectedMeetingRoom)
        .map((booking) => `${booking.oraInizio}-${booking.oraFine}`)
        .sort();
    }

    return this.selectedStationBookingsSummary();
  }

  selectedResourceIsPartiallyOccupied(): boolean {
    if (!this.selectedResourceBookingsSummary().length) {
      return false;
    }

    if (this.selectedMeetingRoom) {
      return !this.selectedMeetingRoomHasOverlap();
    }

    return !this.selectedStationHasOverlap();
  }

  selectedResourceOccupiedLabel(): string {
    return this.selectedMeetingRoom ? 'Questa sala e parzialmente occupata nel giorno selezionato.' : 'Questa postazione e parzialmente occupata nel giorno selezionato.';
  }

  selectedResourceSlotsLabel(): string {
    return this.selectedMeetingRoom ? 'Fasce gia occupate per questa sala' : 'Fasce gia occupate per questa postazione';
  }

  bookingButtonLabel(): string {
    return this.selectedMeetingRoom ? 'Prenota sala' : 'Prenota postazione';
  }

  applySuggestedStartTime(): void {
    if (!this.suggestedStartTime || !this.bookingsAreReady() || this.bookingControlsLocked()) {
      return;
    }
    this.startTime = this.suggestedStartTime;
    if (this.endTime <= this.startTime) {
      this.endTime = nextBookingTimeOption(this.startTime) ?? BOOKING_DAY_END;
    }
    this.clearMessages();
    this.suggestedStartTime = this.findSuggestedStartTimeForSelectedResource();
    this.refreshView();
  }

  onBookingDateChange(): void {
    this.lockedBookingDate = null;
    this.clearMessages();
    const dateError = this.validateBookingDateSelection();
    if (dateError) {
      this.error = dateError;
      this.bookingDate = this.minBookingDate;
    }
    this.suggestedStartTime = null;
    this.loadBookingsForCurrentPlan();
  }

  onBookingTimeChange(): void {
    this.clearMessages();
    this.normalizeBookingTimeWindow();
    const timeError = this.validateBookingTimeSelection();
    if (timeError) {
      this.error = timeError;
    }
    this.suggestedStartTime = this.findSuggestedStartTimeForSelectedResource();
    this.refreshView();
  }

  public selectedRoomStyle(): Record<string, string> {
    const zoomInput = this.selectedRoomZoomInput();
    if (!zoomInput) {
      return {};
    }

    return roomZoomStyle(zoomInput);
  }

  public stationOverlayStyle(station: PositionedStation): Record<string, string> {
    const zoomInput = this.selectedRoomZoomInput();
    if (!zoomInput) {
      return {
        left: `${station.position.xPct}%`,
        top: `${station.position.yPct}%`,
      };
    }

    const zoom = roomZoom(zoomInput);
    if (!zoom || zoomInput.stageWidth <= 0 || zoomInput.stageHeight <= 0) {
      return {
        left: `${station.position.xPct}%`,
        top: `${station.position.yPct}%`,
      };
    }

    const stage = this.planStage?.nativeElement;
    const stageLeft = stage?.offsetLeft ?? 0;
    const stageTop = stage?.offsetTop ?? 0;
    const left = stageLeft + station.position.xPct / 100 * zoomInput.stageWidth * zoom.scale + zoom.translateX;
    const top = stageTop + station.position.yPct / 100 * zoomInput.stageHeight * zoom.scale + zoom.translateY;

    return {
      left: `${left}px`,
      top: `${top}px`,
    };
  }

  private selectedRoomZoomInput(): RoomZoomInput | null {
    const room = this.roomsForDisplay().find((item) => item.id === this.selectedRoomId);
    if (!room?.position) {
      return null;
    }

    return {
      roomPosition: room.position,
      stationPositions: this.stationsForRoom(room.id)
        .filter((station): station is PositionedStation => !!station.position)
        .map((station) => station.position),
      viewportWidth: this.planPreview?.nativeElement.clientWidth ?? 0,
      viewportHeight: this.planPreview?.nativeElement.clientHeight ?? 0,
      stageWidth: this.planStage?.nativeElement.offsetWidth ?? 0,
      stageHeight: this.planStage?.nativeElement.offsetHeight ?? 0,
    };
  }

  getPianoLabel(numero: number): string {
    if (numero === 0) return 'Piano terra';
    if (numero === 1) return 'Primo piano';
    if (numero === 2) return 'Secondo piano';
    return `Piano ${numero}`;
  }

  getPianoDisplayName(piano: Piano): string {
    return piano.nome?.trim() || this.getPianoLabel(piano.numero);
  }

  availableStartTimes(): readonly string[] {
    if (this.bookingControlsLocked()) {
      return this.bookingStartOptions.filter((time) => time === this.startTime);
    }

    if (!this.selectedPostazione && !this.selectedMeetingRoom) {
      return this.bookingStartOptions.filter((time) => time < this.endTime);
    }

    return this.bookingStartOptions.filter((time) =>
      this.bookingEndOptions.some((endTime) =>
        endTime > time && this.canBookCandidateWindow(time, endTime),
      ),
    );
  }

  availableEndTimes(): readonly string[] {
    if (this.bookingControlsLocked()) {
      return this.bookingEndOptions.filter((time) => time === this.endTime);
    }

    if (!this.selectedPostazione && !this.selectedMeetingRoom) {
      return this.bookingEndOptions.filter((time) => time > this.startTime);
    }

    return this.bookingEndOptions.filter((time) =>
      time > this.startTime && this.canBookCandidateWindow(this.startTime, time),
    );
  }

  private loadPlan(pianoId: number): void {
    const requestId = ++this.currentPlanRequestId;
    this.planimetriaSubscription?.unsubscribe();
    this.imageSubscription?.unsubscribe();
    this.layoutSubscription?.unsubscribe();
    this.seatsSubscription?.unsubscribe();
    this.bookingsSubscription?.unsubscribe();
    this.unavailableMessage = '';
    this.planimetriaSubscription = this.api.getPlanimetria(pianoId).subscribe({
      next: (planimetria) => {
          if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
            return;
          }

          if (!planimetria) {
            this.clearLoadedPlanState();
            this.unavailableMessage = this.noPlanMessage();
            this.error = '';
            this.refreshView();
            return;
        }
        this.error = '';
        this.planimetria = planimetria;
        if (this.isPreviewablePlan(planimetria)) {
          this.loadImage(pianoId, requestId);
        } else {
          this.revokeImageUrl();
          this.unavailableMessage =
            'Planimetria tecnica caricata. Per visualizzarla qui serve una versione SVG, PNG o JPG.';
        }
        this.loadLayoutAndSeats(pianoId, requestId);
        this.refreshView();
      },
      error: (err) => {
        if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
          return;
        }
        this.clearLoadedPlanState();
        if (apiErrorStatus(err) === 404) {
          this.unavailableMessage = '';
          this.error = 'Il piano selezionato non esiste piu o non e disponibile.';
        } else {
          this.unavailableMessage = '';
          this.error = apiErrorMessage(err, 'Impossibile caricare la planimetria del piano selezionato.');
        }
        this.refreshView();
      },
    });
  }

  private loadImage(pianoId: number, requestId: number): void {
    this.imageSubscription?.unsubscribe();
    this.imageSubscription = this.api.getPlanimetriaImage(pianoId).subscribe({
      next: (blob) => {
        if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
          return;
        }
        this.revokeImageUrl();
        this.imageSrc = URL.createObjectURL(blob);
        this.refreshView();
      },
      error: () => {
        if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
          return;
        }
        this.revokeImageUrl();
        this.refreshView();
      },
    });
  }

  private loadLayoutAndSeats(pianoId: number, requestId: number): void {
    this.layoutSubscription?.unsubscribe();
    this.layoutSubscription = this.api.getPlanimetriaLayout(pianoId).subscribe({
      next: (layout) => {
        if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
          return;
        }
        this.layout = layout;
        this.selectedRoomId = null;
        this.refreshView();
      },
      error: () => {
        if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
          return;
        }
        this.layout = null;
        this.refreshView();
      },
    });

    this.seatsSubscription?.unsubscribe();
    this.seatsSubscription = forkJoin({
      stanze: this.api.listStanze(pianoId),
      postazioni: this.api.listPostazioniByPiano(pianoId),
      gruppiPostazione: this.api.listSeatGroupsByPiano(pianoId),
    })
      .subscribe({
        next: ({ stanze, postazioni, gruppiPostazione }) => {
          if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
            return;
          }
          this.stanze = stanze;
          this.meetingRooms = stanze.filter((stanza) => stanza.tipo === 'MEETING_ROOM');
          this.postazioni = postazioni;
          this.seatGroupIdsBySeatId = Object.fromEntries(
            postazioni.map((postazione) => [
              postazione.id,
              gruppiPostazione
                .filter((group) => group.postazioneId === postazione.id)
                .map((group) => group.gruppoId),
            ]),
          );
          this.loadBookingsForCurrentPlan();
          this.refreshView();
        },
        error: () => {
          if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
            return;
          }
          this.stanze = [];
          this.meetingRooms = [];
          this.postazioni = [];
          this.seatGroupIdsBySeatId = {};
          this.refreshView();
        },
      });
  }

  findPostazione(station: LayoutStation): Postazione | null {
    const byLayoutElementId =
      this.postazioni.find((postazione) => postazione.layoutElementId && postazione.layoutElementId === station.id) ??
      null;
    if (byLayoutElementId) {
      return byLayoutElementId;
    }

    const fallbackByCodice = this.postazioni.filter((postazione) => postazione.codice === station.label);
    return fallbackByCodice.length === 1 ? fallbackByCodice[0] : null;
  }

  private findStanzaForRoom(room: DisplayRoom): Stanza | null {
    const tipo = this.roomIsMeeting(room) ? 'MEETING_ROOM' : 'ROOM';
    const byLayoutElementId =
      this.stanze.find((stanza) => stanza.tipo === tipo && stanza.layoutElementId && stanza.layoutElementId === room.id) ??
      null;
    if (byLayoutElementId) {
      return byLayoutElementId;
    }

    const fallbackByName = this.stanze.filter((stanza) => stanza.tipo === tipo && stanza.nome === room.label);
    return fallbackByName.length === 1 ? fallbackByName[0] : null;
  }

  private resetPlan(): void {
    this.currentPlanRequestId += 1;
    this.currentBookingsRequestId += 1;
    this.planimetriaSubscription?.unsubscribe();
    this.imageSubscription?.unsubscribe();
    this.layoutSubscription?.unsubscribe();
    this.seatsSubscription?.unsubscribe();
    this.bookingsSubscription?.unsubscribe();
    this.clearMessages();
    this.clearLoadedPlanState();
    this.unavailableMessage = '';
  }

  private clearLoadedPlanState(): void {
    this.clearAvailabilityState();
    this.planimetria = null;
    this.layout = null;
    this.stanze = [];
    this.meetingRooms = [];
    this.postazioni = [];
    this.bookings = [];
    this.seatGroupIdsBySeatId = {};
    this.selectedRoomId = null;
    this.selectedStation = null;
    this.selectedPostazione = null;
    this.selectedMeetingRoom = null;
    this.suggestedStartTime = null;
    this.lockedBookingDate = null;
    this.revokeImageUrl();
  }

  private clearMessages(): void {
    this.message = '';
    this.error = '';
  }

  private clearAvailabilityState(): void {
    this.availabilityError = '';
    this.bookingsLoading = false;
    this.bookingsLoaded = false;
  }

  private revokeImageUrl(): void {
    if (this.imageSrc) {
      URL.revokeObjectURL(this.imageSrc);
      this.imageSrc = '';
    }
  }

  private refreshView(): void {
    this.cdr.detectChanges();
  }

  loadBookingsForCurrentPlan(): void {
    const dateError = this.validateBookingDateSelection(false);
    if (dateError) {
      this.bookings = [];
      this.clearAvailabilityState();
      this.error = dateError;
      this.refreshView();
      return;
    }

    if (!this.selectedPianoId || (!this.postazioni.length && !this.meetingRooms.length)) {
      this.bookings = [];
      this.clearAvailabilityState();
      this.refreshView();
      return;
    }

    const selectedPianoId = this.selectedPianoId;
    const requestId = ++this.currentBookingsRequestId;
    const postazioneIds = new Set(this.postazioni.map((postazione) => postazione.id));
    const meetingRoomIds = new Set(this.meetingRooms.map((stanza) => stanza.id));

    this.bookingsSubscription?.unsubscribe();
    this.bookingsLoading = true;
    this.bookingsLoaded = false;
    this.availabilityError = '';
    this.bookingsSubscription = this.loadBookingsForCurrentRole(this.bookingDate || undefined, this.postazioni, this.meetingRooms).subscribe({
      next: (bookings) => {
        if (requestId !== this.currentBookingsRequestId || this.selectedPianoId !== selectedPianoId) {
          return;
        }
        this.bookings = bookings.filter((booking) =>
          (booking.postazioneId != null && postazioneIds.has(booking.postazioneId))
          || (booking.meetingRoomStanzaId != null && meetingRoomIds.has(booking.meetingRoomStanzaId))
          || this.isCurrentUserBooking(booking),
        );
        this.bookingsLoading = false;
        this.bookingsLoaded = true;
        this.suggestedStartTime = this.findSuggestedStartTimeForSelectedResource();
        this.normalizeSelectableTimesForCurrentResource();
        this.updateOverlapMessage();
        this.refreshView();
      },
      error: (err) => {
        if (requestId !== this.currentBookingsRequestId || this.selectedPianoId !== selectedPianoId) {
          return;
        }
        this.bookings = [];
        this.bookingsLoading = false;
        this.bookingsLoaded = false;
        this.suggestedStartTime = null;
        this.availabilityError = apiErrorMessage(
          err,
          "Impossibile verificare la disponibilita delle risorse. Per evitare risultati falsati la mappa non mostra risorse prenotabili.",
        );
        this.refreshView();
      },
    });
  }

  private noPlanMessage(): string {
    const sede = this.sedi.find((item) => item.id === this.selectedSedeId);
    const piano = this.piani.find((item) => item.id === this.selectedPianoId);
    const sedeLabel = sede ? `${sede.nome} - ${sede.citta}` : 'questa sede';
    const pianoLabel = piano ? this.getPianoDisplayName(piano) : 'questo piano';
    return `Per ${sedeLabel} e per ${pianoLabel} non c'e ancora la planimetria.`;
  }

  private isPreviewablePlan(planimetria: PlanimetriaResponse): boolean {
    const imageName = planimetria.imageName?.toLowerCase() ?? '';
    return (
      planimetria.formatoOriginale !== 'DXF'
      && planimetria.formatoOriginale !== 'DWG'
    ) || /\.(svg|png|jpg|jpeg)$/.test(imageName);
  }

  private stationHasBookings(station: LayoutStation): boolean {
    return this.bookingsForStation(station).length > 0;
  }

  private stationHasOverlap(station: LayoutStation): boolean {
    return !!this.bookingForStation(station);
  }

  private selectedStationHasOverlap(): boolean {
    return this.selectedStation ? this.stationHasOverlap(this.selectedStation) : false;
  }

  private selectedUserHasOverlap(startTime: string = this.startTime, endTime: string = this.endTime): boolean {
    return this.bookingsForCurrentUser().some((booking) =>
      this.bookingOverlapsWindow(booking, startTime, endTime),
    );
  }

  private bookingsForMeetingRoom(stanza: Stanza): Prenotazione[] {
    return this.bookings.filter((booking) =>
      booking.stato === 'CONFERMATA' && booking.meetingRoomStanzaId === stanza.id,
    );
  }

  private meetingRoomHasBookings(stanza: Stanza): boolean {
    return this.bookingsForMeetingRoom(stanza).length > 0;
  }

  private meetingRoomHasOverlap(stanza: Stanza): boolean {
    return this.bookingsForMeetingRoom(stanza).some((booking) => this.bookingOverlapsSelectedWindow(booking));
  }

  private selectedMeetingRoomHasOverlap(): boolean {
    return this.selectedMeetingRoom ? this.meetingRoomHasOverlap(this.selectedMeetingRoom) : false;
  }

  private bookingsForCurrentUser(): Prenotazione[] {
    return this.bookings.filter((booking) => this.isCurrentUserBooking(booking));
  }

  private findSuggestedStartTime(station: LayoutStation | null): string | null {
    if (!station || !this.bookingsAreReady() || !this.hasValidBookingWindow()) {
      return null;
    }

    return this.findSuggestedStartTimeFromBookings([
      ...this.bookingsForStation(station),
      ...this.bookingsForCurrentUser(),
    ]);
  }

  private findSuggestedStartTimeForMeetingRoom(stanza: Stanza | null): string | null {
    if (!stanza || !this.bookingsAreReady() || !this.hasValidBookingWindow()) {
      return null;
    }

    return this.findSuggestedStartTimeFromBookings([
      ...this.bookingsForMeetingRoom(stanza),
      ...this.bookingsForCurrentUser(),
    ]);
  }

  private findSuggestedStartTimeForSelectedResource(): string | null {
    if (this.selectedMeetingRoom) {
      return this.findSuggestedStartTimeForMeetingRoom(this.selectedMeetingRoom);
    }

    return this.findSuggestedStartTime(this.selectedStation);
  }

  private findSuggestedStartTimeFromBookings(bookings: Prenotazione[]): string | null {
    const overlappingBookings = Array.from(new Map(
      bookings.map((booking) => [booking.id, booking]),
    ).values())
      .filter((booking) => this.bookingOverlapsSelectedWindow(booking))
      .sort((left, right) => left.oraInizio.localeCompare(right.oraInizio));

    if (!overlappingBookings.length) {
      return null;
    }

    const rawSuggestion = overlappingBookings
      .map((booking) => booking.oraFine)
      .sort()
      .at(-1) ?? null;

    if (!rawSuggestion) {
      return null;
    }

    return ceilBookingStartOption(rawSuggestion);
  }

  private isCurrentUserBooking(booking: Prenotazione): boolean {
    if (booking.stato !== 'CONFERMATA') {
      return false;
    }

    const currentUser = this.auth.currentUser();
    const currentEmail = currentUser?.email?.trim().toLowerCase() ?? '';
    const currentId = currentUser?.id ?? null;
    if (currentId && currentId > 0 && booking.utenteId === currentId) {
      return true;
    }

    const bookingEmail = booking.utenteEmail?.trim().toLowerCase() ?? '';
    return !!currentEmail && !!bookingEmail && bookingEmail === currentEmail;
  }

  private bookingOverlapsSelectedWindow(booking: Prenotazione): boolean {
    return this.bookingOverlapsWindow(booking, this.startTime, this.endTime);
  }

  private bookingOverlapsWindow(booking: Prenotazione, startTime: string, endTime: string): boolean {
    return startTime < booking.oraFine && endTime > booking.oraInizio;
  }

  private canBookCandidateWindow(startTime: string, endTime: string): boolean {
    if (!isWithinBookingWindow(startTime, endTime)) {
      return false;
    }

    if (this.selectedUserHasOverlap(startTime, endTime)) {
      return false;
    }

    if (this.selectedMeetingRoom) {
      if (!this.meetingRoomIsBookable(this.selectedMeetingRoom)) {
        return false;
      }
      return !this.bookingsForMeetingRoom(this.selectedMeetingRoom).some((booking) =>
        this.bookingOverlapsWindow(booking, startTime, endTime),
      );
    }

    if (this.selectedStation) {
      return !this.bookingsForStation(this.selectedStation).some((booking) =>
        this.bookingOverlapsWindow(booking, startTime, endTime),
      );
    }

    return true;
  }

  private loadBookingsForCurrentRole(
    data?: string,
    postazioni: Postazione[] = this.postazioni,
    meetingRooms: Stanza[] = this.meetingRooms,
  ): Observable<Prenotazione[]> {
    const postazioneIds = postazioni.map((postazione) => postazione.id);
    const meetingRoomIds = meetingRooms.map((stanza) => stanza.id);
    if (!postazioneIds.length && !meetingRoomIds.length) {
      return of([]);
    }

    const resourceBookings$ = this.api.listBookingsByResources(data, postazioneIds, meetingRoomIds);
    if (this.hasOperationalBookingAccess()) {
      return resourceBookings$;
    }

    return forkJoin({
      resourceBookings: resourceBookings$,
      myBookings: this.api.listMyBookings(data),
    }).pipe(
      map(({ resourceBookings, myBookings }) => Array.from(new Map(
        [...resourceBookings, ...myBookings].map((booking) => [booking.id, booking]),
      ).values())),
    );
  }

  private hasOperationalBookingAccess(): boolean {
    return this.auth.hasAnyRole(OPERATIONAL_BOOKING_ROLES);
  }

  private bookingsAreReady(): boolean {
    return this.bookingsLoaded && !this.bookingsLoading && !this.availabilityError;
  }

  private meetingRoomIsBookable(stanza: Stanza): boolean {
    return (stanza.stato ?? 'DISPONIBILE') === 'DISPONIBILE';
  }

  private statoLabel(stato: StatoPostazione): string {
    switch (stato) {
      case 'DISPONIBILE':
        return 'Disponibile';
      case 'NON_DISPONIBILE':
        return 'Non disponibile';
      case 'MANUTENZIONE':
        return 'Manutenzione';
      case 'CAMBIO_DESTINAZIONE':
        return 'Cambio destinazione';
      default:
        return stato;
    }
  }

  private availabilityStatusMessage(): string {
    if (this.bookingsLoading) {
      return 'Verifica della disponibilita in corso. Attendi qualche istante.';
    }

    return this.availabilityError
      || 'Disponibilita non verificata al momento. Riprova tra qualche istante.';
  }

  private validateBookingDateSelection(replaceInvalidDate = true): string | null {
    if (!this.bookingDate) {
      return 'Seleziona una data per la prenotazione.';
    }
    if (this.bookingDate < this.minBookingDate) {
      if (replaceInvalidDate) {
        this.bookingDate = this.minBookingDate;
      }
      return 'Le prenotazioni sono consentite solo a partire dal primo giorno lavorativo disponibile.';
    }
    if (isWeekendIsoDate(this.bookingDate)) {
      if (replaceInvalidDate) {
        this.bookingDate = this.minBookingDate;
      }
      return 'Le prenotazioni non sono consentite il sabato e la domenica.';
    }
    return null;
  }

  private validateBookingTimeSelection(): string | null {
    if (!this.startTime || !this.endTime) {
      return 'Seleziona una fascia oraria valida.';
    }
    if (!isWithinBookingWindow(this.startTime, this.endTime)) {
      if (this.startTime >= this.endTime) {
        return "L'ora di inizio deve essere precedente all'ora di fine.";
      }
      return 'Le prenotazioni sono consentite solo tra le 09:00 e le 18:00.';
    }
    return null;
  }

  private hasValidBookingWindow(): boolean {
    return !this.validateBookingDateSelection(false) && !this.validateBookingTimeSelection();
  }

  private normalizeBookingTimeWindow(): void {
    if (this.startTime < BOOKING_DAY_START) {
      this.startTime = BOOKING_DAY_START;
    }
    if (this.endTime > BOOKING_DAY_END) {
      this.endTime = BOOKING_DAY_END;
    }
    if (this.startTime >= this.endTime) {
      this.endTime = nextBookingTimeOption(this.startTime) ?? BOOKING_DAY_END;
    }

    this.normalizeSelectableTimesForCurrentResource();
    this.updateOverlapMessage();
  }

  private normalizeSelectableTimesForCurrentResource(): void {
    if (!this.selectedPostazione && !this.selectedMeetingRoom) {
      return;
    }

    const startOptions = this.availableStartTimes();
    if (!startOptions.length) {
      return;
    }

    if (!startOptions.includes(this.startTime)) {
      this.startTime = this.suggestedStartTime && startOptions.includes(this.suggestedStartTime)
        ? this.suggestedStartTime
        : startOptions[0];
    }

    const endOptions = this.availableEndTimes();
    if (!endOptions.length) {
      this.endTime = nextBookingTimeOption(this.startTime) ?? BOOKING_DAY_END;
      return;
    }

    if (!endOptions.includes(this.endTime)) {
      this.endTime = endOptions[0];
    }
  }

  private updateOverlapMessage(): void {
    if (!this.selectedPostazione && !this.selectedMeetingRoom) {
      return;
    }

    if (this.bookingControlsLocked()) {
      this.error = '';
      return;
    }

    if (this.selectedMeetingRoomHasOverlap()) {
      this.message = '';
      this.error = `La sala e occupata nella fascia selezionata. Prima disponibilita suggerita dalle ${this.suggestedStartTime ?? '09:00'}.`;
      return;
    }

    if (this.selectedStationHasOverlap()) {
      this.message = '';
      this.error = `La postazione e occupata nella fascia selezionata. Prima disponibilita suggerita dalle ${this.suggestedStartTime ?? '09:00'}.`;
      return;
    }

    this.clearMessages();
  }

  bookingControlsLocked(): boolean {
    return this.lockedBookingDate === this.bookingDate;
  }

  unlockBookingControls(): void {
    this.lockedBookingDate = null;
    this.message = '';
    this.error = '';
    this.suggestedStartTime = this.findSuggestedStartTimeForSelectedResource();
    this.refreshView();
  }
}
