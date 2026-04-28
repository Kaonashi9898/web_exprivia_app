import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable, of, Subscription, switchMap, map } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Edificio, Piano, PlanimetriaLayout, PlanimetriaResponse, Postazione, Prenotazione, Sede } from '../../core/app.models';
import { apiErrorMessage } from '../../core/api-error.utils';
import { AuthService } from '../../core/auth.service';
import { isWeekendIsoDate, nextBookableIsoDate } from '../../core/date.utils';
import { OPERATIONAL_BOOKING_ROLES } from '../../core/role-access';

type LayoutRoom = NonNullable<PlanimetriaLayout['rooms']>[number];
type LayoutMeeting = NonNullable<PlanimetriaLayout['meetings']>[number];
type DisplayRoom = LayoutRoom | LayoutMeeting;
type PositionedRoom = DisplayRoom & { position: { xPct: number; yPct: number } };
type LayoutStation = NonNullable<PlanimetriaLayout['stations']>[number];
type PositionedStation = LayoutStation & { position: { xPct: number; yPct: number } };

@Component({
  selector: 'app-locations',
  imports: [FormsModule, NgStyle],
  templateUrl: './locations.html',
  styleUrl: './locations.css',
})
export class LocationsComponent implements OnInit, OnDestroy {
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
  private bookingsSubscription: Subscription | null = null;
  private createBookingSubscription: Subscription | null = null;
  private currentPlanRequestId = 0;
  private currentBookingsRequestId = 0;

  sedi: Sede[] = [];
  edifici: Edificio[] = [];
  piani: Piano[] = [];
  postazioni: Postazione[] = [];
  bookings: Prenotazione[] = [];
  selectedSedeId: number | null = null;
  selectedEdificioId: number | null = null;
  selectedPianoId: number | null = null;
  selectedRoomId: string | null = null;
  selectedStation: LayoutStation | null = null;
  selectedPostazione: Postazione | null = null;
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
  bookingDate = this.minBookingDate;
  startTime = '09:00';
  endTime = '18:00';

  ngOnInit(): void {
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
    this.selectedRoomId = room.id;
    this.selectedStation = null;
    this.selectedPostazione = null;
    this.refreshView();
  }

  resetZoom(): void {
    this.selectedRoomId = null;
    this.selectedStation = null;
    this.selectedPostazione = null;
    this.refreshView();
  }

  selectStation(station: LayoutStation): void {
    const postazione = this.findPostazione(station);
    if (!postazione || postazione.stato !== 'DISPONIBILE') {
      return;
    }

    this.selectedStation = station;
    this.selectedRoomId = station.roomId ?? this.selectedRoomId;
    this.selectedPostazione = postazione;
    if (!this.bookingsAreReady()) {
      this.suggestedStartTime = null;
      this.refreshView();
      return;
    }
    this.suggestedStartTime = this.findSuggestedStartTime(station);
    if (this.selectedStationHasOverlap() && this.suggestedStartTime) {
      this.message = '';
      this.error = `La postazione e' occupata nella fascia selezionata. Prima disponibilita suggerita dalle ${this.suggestedStartTime}.`;
    } else {
      this.clearMessages();
    }
    this.refreshView();
  }

  createBooking(): void {
    if (!this.selectedPostazione) {
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

    this.clearMessages();
    this.createBookingSubscription?.unsubscribe();
    this.createBookingSubscription = this.api
      .createBooking({
        postazioneId: this.selectedPostazione.id,
        dataPrenotazione: this.bookingDate,
        oraInizio: this.startTime,
        oraFine: this.endTime,
      })
      .subscribe({
        next: () => {
          this.message = 'Prenotazione confermata.';
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
    if (!this.selectedRoomId) {
      return [];
    }
    return stations.filter((station) => station.roomId === this.selectedRoomId);
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
      && this.bookingsAreReady()
      && this.hasValidBookingWindow()
      && !this.stationHasOverlap(station);
  }

  stationIsUnavailable(station: LayoutStation): boolean {
    const postazione = this.findPostazione(station);
    return !postazione || postazione.stato !== 'DISPONIBILE' || !this.bookingsAreReady();
  }

  stationIsPartiallyBooked(station: LayoutStation): boolean {
    if (!this.bookingsAreReady()) {
      return false;
    }
    const postazione = this.findPostazione(station);
    return !!postazione && postazione.stato === 'DISPONIBILE' && this.stationHasBookings(station);
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
      return `Occupata nella fascia selezionata da ${overlappingBooking.utenteFullName}, dalle ${overlappingBooking.oraInizio} alle ${overlappingBooking.oraFine}`;
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
    return `${this.bookingDate} dalle ${this.startTime} alle ${this.endTime}`;
  }

  selectedStationCanBeBooked(): boolean {
    return !!this.selectedPostazione
      && this.bookingsAreReady()
      && this.hasValidBookingWindow()
      && !this.selectedStationHasOverlap();
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

  applySuggestedStartTime(): void {
    if (!this.suggestedStartTime || !this.bookingsAreReady()) {
      return;
    }
    this.startTime = this.suggestedStartTime;
    if (this.endTime <= this.startTime) {
      this.endTime = this.addOneHour(this.startTime);
    }
    this.clearMessages();
    this.suggestedStartTime = this.findSuggestedStartTime(this.selectedStation);
    this.refreshView();
  }

  onBookingDateChange(): void {
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
    const timeError = this.validateBookingTimeSelection();
    if (timeError) {
      this.error = timeError;
    }
    this.suggestedStartTime = this.findSuggestedStartTime(this.selectedStation);
    this.refreshView();
  }

  selectedRoomStyle(): Record<string, string> {
    const room = this.roomsForDisplay().find((item) => item.id === this.selectedRoomId);
    if (!room?.position) {
      return {};
    }

    return {
      transform: 'scale(2.15)',
      transformOrigin: `${room.position.xPct}% ${room.position.yPct}%`,
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
            this.unavailableMessage = this.noPlanMessage();
            this.refreshView();
            return;
          }
        this.planimetria = planimetria;
        if (planimetria.formatoOriginale === 'DXF' || planimetria.formatoOriginale === 'DWG') {
          this.revokeImageUrl();
          this.unavailableMessage =
            'Planimetria tecnica caricata. Per visualizzarla qui serve anche una versione PNG, JPG o SVG.';
        } else {
          this.loadImage(pianoId, requestId);
        }
        this.loadLayoutAndSeats(pianoId, requestId);
        this.refreshView();
      },
      error: () => {
        if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
          return;
        }
        this.unavailableMessage = this.noPlanMessage();
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
    this.seatsSubscription = this.api
      .listStanze(pianoId)
      .pipe(
        switchMap((stanze) =>
          stanze.length ? forkJoin(stanze.map((stanza) => this.api.listPostazioni(stanza.id))) : of([]),
        ),
      )
      .subscribe({
        next: (groups) => {
          if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
            return;
          }
          this.postazioni = groups.flat();
          this.loadBookingsForCurrentPlan();
          this.refreshView();
        },
        error: () => {
          if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
            return;
          }
          this.postazioni = [];
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

  private resetPlan(): void {
    this.currentPlanRequestId += 1;
    this.currentBookingsRequestId += 1;
    this.planimetriaSubscription?.unsubscribe();
    this.imageSubscription?.unsubscribe();
    this.layoutSubscription?.unsubscribe();
    this.seatsSubscription?.unsubscribe();
    this.bookingsSubscription?.unsubscribe();
    this.clearMessages();
    this.clearAvailabilityState();
    this.planimetria = null;
    this.layout = null;
    this.postazioni = [];
    this.bookings = [];
    this.selectedRoomId = null;
    this.selectedStation = null;
    this.selectedPostazione = null;
    this.suggestedStartTime = null;
    this.unavailableMessage = '';
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

    if (!this.selectedPianoId || !this.postazioni.length) {
      this.bookings = [];
      this.clearAvailabilityState();
      this.refreshView();
      return;
    }

    const selectedPianoId = this.selectedPianoId;
    const requestId = ++this.currentBookingsRequestId;
    const postazioneIds = new Set(this.postazioni.map((postazione) => postazione.id));

    this.bookingsSubscription?.unsubscribe();
    this.bookingsLoading = true;
    this.bookingsLoaded = false;
    this.availabilityError = '';
    this.bookingsSubscription = this.loadBookingsForCurrentRole(this.bookingDate || undefined, this.postazioni).subscribe({
      next: (bookings) => {
        if (requestId !== this.currentBookingsRequestId || this.selectedPianoId !== selectedPianoId) {
          return;
        }
        this.bookings = bookings.filter((booking) => postazioneIds.has(booking.postazioneId));
        this.bookingsLoading = false;
        this.bookingsLoaded = true;
        this.suggestedStartTime = this.findSuggestedStartTime(this.selectedStation);
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
          "Impossibile verificare la disponibilita delle postazioni. Per evitare risultati falsati la mappa non mostra posti prenotabili.",
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

  private stationHasBookings(station: LayoutStation): boolean {
    return this.bookingsForStation(station).length > 0;
  }

  private stationHasOverlap(station: LayoutStation): boolean {
    return !!this.bookingForStation(station);
  }

  private selectedStationHasOverlap(): boolean {
    return this.selectedStation ? this.stationHasOverlap(this.selectedStation) : false;
  }

  private findSuggestedStartTime(station: LayoutStation | null): string | null {
    if (!station || !this.bookingsAreReady() || !this.hasValidBookingWindow()) {
      return null;
    }

    const overlappingBookings = this.bookingsForStation(station)
      .filter((booking) => this.bookingOverlapsSelectedWindow(booking))
      .sort((left, right) => left.oraInizio.localeCompare(right.oraInizio));

    if (!overlappingBookings.length) {
      return null;
    }

    return overlappingBookings
      .map((booking) => booking.oraFine)
      .sort()
      .at(-1) ?? null;
  }

  private bookingOverlapsSelectedWindow(booking: Prenotazione): boolean {
    return this.startTime < booking.oraFine && this.endTime > booking.oraInizio;
  }

  private loadBookingsForCurrentRole(data?: string, postazioni: Postazione[] = this.postazioni): Observable<Prenotazione[]> {
    if (this.hasOperationalBookingAccess()) {
      return this.api.listBookings(data);
    }

    return forkJoin(postazioni.map((postazione) => this.api.listBookingsByPostazione(postazione.id, data))).pipe(
      map((groups) => groups.flat()),
    );
  }

  private hasOperationalBookingAccess(): boolean {
    return this.auth.hasAnyRole(OPERATIONAL_BOOKING_ROLES);
  }

  private bookingsAreReady(): boolean {
    return this.bookingsLoaded && !this.bookingsLoading && !this.availabilityError;
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
    if (this.startTime >= this.endTime) {
      return "L'ora di inizio deve essere precedente all'ora di fine.";
    }
    return null;
  }

  private hasValidBookingWindow(): boolean {
    return !this.validateBookingDateSelection(false) && !this.validateBookingTimeSelection();
  }

  private addOneHour(value: string): string {
    const [hours, minutes] = value.split(':').map(Number);
    const date = new Date();
    date.setHours(hours, minutes, 0, 0);
    date.setHours(date.getHours() + 1);
    return date.toTimeString().slice(0, 5);
  }
}
