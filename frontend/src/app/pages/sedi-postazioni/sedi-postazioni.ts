import { ChangeDetectorRef, Component, computed, inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, of, Subscription, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Edificio, Gruppo, GruppoPostazione, Piano, Postazione, Prenotazione, Sede, Stanza, StatoPostazione } from '../../core/app.models';
import { apiErrorMessage, bookingCancellationErrorMessage } from '../../core/api-error.utils';
import { AuthService } from '../../core/auth.service';
import {
  BOOKING_DAY_END,
  BOOKING_DAY_START,
  BOOKING_END_OPTIONS,
  BOOKING_START_OPTIONS,
  isWithinBookingWindow,
  nextBookingTimeOption,
} from '../../core/booking-time.utils';
import { isWeekendIsoDate, nextBookableIsoDate } from '../../core/date.utils';

interface RoomStats {
  stanza: Stanza;
  meetingRoom: boolean;
  total: number;
  free: number;
  occupied: number;
  unavailable: number;
  maintenance: number;
  meetingRoomSlots: string[];
  freeCodes: string[];
  occupiedCodes: string[];
  blockedCodes: string[];
}

type RoomView = 'rooms' | 'meetingRooms';

interface CreateSedeForm {
  nome: string;
  citta: string;
  indirizzo: string;
  latitudine: string;
  longitudine: string;
}

interface CreatePianoForm {
  nome: string;
  numero: string;
}

type LocationDeletionTarget =
  | { type: 'sede'; id: number; title: string; message: string }
  | { type: 'edificio'; id: number; title: string; message: string }
  | { type: 'piano'; id: number; title: string; message: string };

@Component({
  selector: 'app-sedi-postazioni',
  imports: [FormsModule],
  templateUrl: './sedi-postazioni.html',
  styleUrl: './sedi-postazioni.css',
})
export class SediPostazioniComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private sediSubscription: Subscription | null = null;
  private edificiSubscription: Subscription | null = null;
  private pianiSubscription: Subscription | null = null;
  private reportSubscription: Subscription | null = null;

  protected readonly isAdmin = computed(() => this.auth.hasAnyRole(['ADMIN']));
  protected readonly canManageDeskStates = computed(() =>
    this.auth.hasAnyRole(['ADMIN', 'BUILDING_MANAGER']),
  );

  sedi: Sede[] = [];
  edifici: Edificio[] = [];
  piani: Piano[] = [];
  stanze: Stanza[] = [];
  postazioni: Postazione[] = [];
  bookings: Prenotazione[] = [];
  roomStats: RoomStats[] = [];
  activeRoomView: RoomView = 'rooms';

  selectedSedeId: number | null = null;
  selectedEdificioId: number | null = null;
  selectedPianoId: number | null = null;
  readonly minReportDate = nextBookableIsoDate();
  reportDate = this.minReportDate;
  loading = false;
  error = '';
  showCreateSedeModal = false;
  showCreateEdificioModal = false;
  showCreatePianoModal = false;
  creatingSede = false;
  creatingEdificio = false;
  creatingPiano = false;
  createSedeError = '';
  createEdificioError = '';
  createPianoError = '';
  createSedeForm: CreateSedeForm = this.buildEmptySedeForm();
  createEdificioNome = '';
  createPianoForm: CreatePianoForm = this.buildEmptyPianoForm();
  showSeatStateModal = false;
  selectedRoomForState: RoomStats | null = null;
  updatingSeatId: number | null = null;
  updatingMeetingRoomState = false;
  meetingRoomStateDraft: StatoPostazione = 'DISPONIBILE';
  seatStateDrafts: Record<number, StatoPostazione> = {};
  seatStateError = '';
  seatStateMessage = '';
  availableSeatGroups: Gruppo[] = [];
  seatGroupsBySeatId: Record<number, GruppoPostazione[]> = {};
  seatGroupDrafts: Record<number, number | null> = {};
  roomGroupDraft: number | null = null;
  loadingSeatGroups = false;
  updatingSeatGroupKey = '';
  updatingRoomGroupAction = '';
  seatGroupError = '';
  seatGroupMessage = '';
  bookingActionError = '';
  bookingActionMessage = '';
  bookingPendingDeletion: Prenotazione | null = null;
  locationPendingDeletion: LocationDeletionTarget | null = null;
  deletingLocation = false;
  editingBooking: Prenotazione | null = null;
  deletingBookingId: number | null = null;
  savingBookingId: number | null = null;
  editBookingDate = this.minReportDate;
  editStartTime = BOOKING_DAY_START;
  editEndTime = BOOKING_DAY_END;
  readonly bookingStartOptions = BOOKING_START_OPTIONS;
  readonly bookingEndOptions = BOOKING_END_OPTIONS;

  ngOnInit(): void {
    this.loadSedi();
  }

  ngOnDestroy(): void {
    this.sediSubscription?.unsubscribe();
    this.edificiSubscription?.unsubscribe();
    this.pianiSubscription?.unsubscribe();
    this.reportSubscription?.unsubscribe();
  }

  loadSedi(): void {
    this.sediSubscription?.unsubscribe();
    this.sediSubscription = this.api.listSedi().subscribe({
      next: (sedi) => {
        this.sedi = sedi;
        this.refreshView();
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Impossibile caricare le sedi.');
        this.refreshView();
      },
    });
  }

  createSede(): void {
    if (!this.isAdmin()) {
      return;
    }
    this.openCreateSedeModal();
  }

  deleteSelectedSede(): void {
    if (!this.isAdmin()) {
      return;
    }

    if (!this.selectedSedeId) return;
    const sede = this.sedi.find((item) => item.id === this.selectedSedeId);
    this.locationPendingDeletion = {
      type: 'sede',
      id: this.selectedSedeId,
      title: `Eliminare la sede ${sede?.nome ?? ''}?`,
      message: 'Verranno rimossi anche edifici, piani, stanze, postazioni e planimetrie collegate.',
    };
    this.error = '';
    this.refreshView();
  }

  createEdificio(): void {
    if (!this.isAdmin()) {
      return;
    }

    if (!this.selectedSedeId) {
      return;
    }
    this.openCreateEdificioModal();
  }

  openCreateSedeModal(): void {
    this.createSedeForm = this.buildEmptySedeForm();
    this.createSedeError = '';
    this.showCreateSedeModal = true;
    this.refreshView();
  }

  closeCreateSedeModal(): void {
    if (this.creatingSede) {
      return;
    }
    this.showCreateSedeModal = false;
    this.createSedeError = '';
    this.refreshView();
  }

  submitCreateSede(): void {
    if (this.creatingSede || !this.isAdmin()) {
      return;
    }

    const nome = this.createSedeForm.nome.trim();
    const citta = this.createSedeForm.citta.trim();
    const indirizzo = this.createSedeForm.indirizzo.trim();

    if (!nome || !citta || !indirizzo) {
      this.createSedeError = 'Compila i campi obbligatori: nome, citta e indirizzo.';
      this.refreshView();
      return;
    }

    const latitudine = this.parseCoordinate(this.createSedeForm.latitudine, 'Latitudine');
    if (latitudine.error) {
      this.createSedeError = latitudine.error;
      this.refreshView();
      return;
    }

    const longitudine = this.parseCoordinate(this.createSedeForm.longitudine, 'Longitudine');
    if (longitudine.error) {
      this.createSedeError = longitudine.error;
      this.refreshView();
      return;
    }

    this.creatingSede = true;
    this.createSedeError = '';

    this.api
      .createSede({
        nome: this.withExpriviaPrefix(nome),
        citta,
        indirizzo,
        latitudine: latitudine.value,
        longitudine: longitudine.value,
      })
      .subscribe({
        next: (sede) => {
          this.creatingSede = false;
          this.showCreateSedeModal = false;
          this.sedi = [...this.sedi.filter((item) => item.id !== sede.id), sede];
          this.selectedSedeId = sede.id;
          this.onSedeChange();
        },
        error: (err) => {
          this.creatingSede = false;
          this.createSedeError = apiErrorMessage(err, 'Creazione sede non riuscita.');
          this.refreshView();
        },
      });
  }

  openCreateEdificioModal(): void {
    if (!this.selectedSedeId) {
      return;
    }
    this.createEdificioNome = '';
    this.createEdificioError = '';
    this.showCreateEdificioModal = true;
    this.refreshView();
  }

  closeCreateEdificioModal(): void {
    if (this.creatingEdificio) {
      return;
    }
    this.showCreateEdificioModal = false;
    this.createEdificioError = '';
    this.refreshView();
  }

  submitCreateEdificio(): void {
    if (this.creatingEdificio || !this.isAdmin() || !this.selectedSedeId) {
      return;
    }

    const nome = this.createEdificioNome.trim();
    if (!nome) {
      this.createEdificioError = 'Inserisci il nome del nuovo edificio.';
      this.refreshView();
      return;
    }

    this.creatingEdificio = true;
    this.createEdificioError = '';

    this.api.createEdificio({ nome, sedeId: this.selectedSedeId }).subscribe({
      next: (edificio) => {
        this.creatingEdificio = false;
        this.showCreateEdificioModal = false;
        this.edifici = [...this.edifici.filter((item) => item.id !== edificio.id), edificio];
        this.selectedEdificioId = edificio.id;
        this.onEdificioChange();
      },
      error: (err) => {
        this.creatingEdificio = false;
        this.createEdificioError = apiErrorMessage(err, 'Creazione edificio non riuscita.');
        this.refreshView();
      },
    });
  }

  deleteSelectedEdificio(): void {
    if (!this.isAdmin()) {
      return;
    }

    if (!this.selectedEdificioId) return;
    const edificio = this.edifici.find((item) => item.id === this.selectedEdificioId);
    this.locationPendingDeletion = {
      type: 'edificio',
      id: this.selectedEdificioId,
      title: `Eliminare l'edificio ${edificio?.nome ?? ''}?`,
      message: 'Verranno rimossi anche piani, stanze, postazioni e planimetrie collegate.',
    };
    this.error = '';
    this.refreshView();
  }

  createPiano(): void {
    if (!this.isAdmin() || !this.selectedEdificioId) {
      return;
    }

    this.openCreatePianoModal();
  }

  openCreatePianoModal(): void {
    if (!this.selectedEdificioId) {
      return;
    }

    this.createPianoForm = {
      nome: '',
      numero: String(this.getNextPianoNumero()),
    };
    this.createPianoError = '';
    this.showCreatePianoModal = true;
    this.refreshView();
  }

  closeCreatePianoModal(): void {
    if (this.creatingPiano) {
      return;
    }

    this.showCreatePianoModal = false;
    this.createPianoError = '';
    this.refreshView();
  }

  submitCreatePiano(): void {
    if (this.creatingPiano || !this.isAdmin() || !this.selectedEdificioId) {
      return;
    }

    const nome = this.createPianoForm.nome.trim();
    if (!nome) {
      this.createPianoError = 'Inserisci il nome del nuovo piano.';
      this.refreshView();
      return;
    }

    const numeroText = this.createPianoForm.numero.trim();
    const numero = numeroText ? Number(numeroText) : this.getNextPianoNumero();

    if (!Number.isInteger(numero)) {
      this.createPianoError = 'Numero piano non valido. Inserisci un numero intero.';
      this.refreshView();
      return;
    }

    this.creatingPiano = true;
    this.createPianoError = '';

    this.api
      .createPiano({
        numero,
        nome,
        edificioId: this.selectedEdificioId,
      })
      .subscribe({
        next: (piano) => {
          this.creatingPiano = false;
          this.showCreatePianoModal = false;
          this.piani = [...this.piani, piano].sort((a, b) => a.numero - b.numero);
          this.selectedPianoId = piano.id;
          this.onPianoChange();
        },
        error: (err) => {
          this.creatingPiano = false;
          this.createPianoError = apiErrorMessage(err, 'Creazione piano non riuscita.');
          this.refreshView();
        },
      });
  }

  deleteSelectedPiano(): void {
    if (!this.isAdmin() || !this.selectedPianoId) {
      return;
    }

    const piano = this.piani.find((item) => item.id === this.selectedPianoId);
    const pianoLabel = piano ? this.getPianoDisplayName(piano) : 'il piano selezionato';

    this.locationPendingDeletion = {
      type: 'piano',
      id: this.selectedPianoId,
      title: `Eliminare ${pianoLabel}?`,
      message: 'Verranno rimosse anche stanze, postazioni e planimetria collegate.',
    };
    this.error = '';
    this.refreshView();
  }

  closeLocationDeleteModal(): void {
    if (this.deletingLocation) {
      return;
    }

    this.locationPendingDeletion = null;
    this.refreshView();
  }

  confirmLocationDeletion(): void {
    const target = this.locationPendingDeletion;
    if (!this.isAdmin() || !target || this.deletingLocation) {
      return;
    }

    this.deletingLocation = true;
    this.error = '';

    const request = target.type === 'sede'
      ? this.api.deleteSede(target.id)
      : target.type === 'edificio'
        ? this.api.deleteEdificio(target.id)
        : this.api.deletePiano(target.id);

    request.subscribe({
      next: () => {
        this.deletingLocation = false;
        this.locationPendingDeletion = null;

        if (target.type === 'sede') {
          this.sedi = this.sedi.filter((item) => item.id !== target.id);
          this.selectedSedeId = null;
          this.resetSelection('sede');
          return;
        }

        if (target.type === 'edificio') {
          this.edifici = this.edifici.filter((item) => item.id !== target.id);
          this.selectedEdificioId = null;
          this.resetSelection('edificio');
          return;
        }

        this.piani = this.piani.filter((item) => item.id !== target.id);
        this.selectedPianoId = null;
        this.resetSelection('piano');
      },
      error: (err) => {
        this.deletingLocation = false;
        this.locationPendingDeletion = null;
        this.error = apiErrorMessage(err, this.locationDeletionErrorMessage(target.type));
        this.refreshView();
      },
    });
  }

  onSedeChange(): void {
    this.resetSelection('sede');
    if (!this.selectedSedeId) return;

    this.edificiSubscription?.unsubscribe();
    this.edificiSubscription = this.api.listEdifici(this.selectedSedeId).subscribe({
      next: (edifici) => {
        this.edifici = edifici;
        this.refreshView();
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Impossibile caricare gli edifici.');
        this.refreshView();
      },
    });
  }

  onEdificioChange(): void {
    this.resetSelection('edificio');
    if (!this.selectedEdificioId) return;

    this.pianiSubscription?.unsubscribe();
    this.pianiSubscription = this.api.listPiani(this.selectedEdificioId).subscribe({
      next: (piani) => {
        this.piani = piani;
        this.refreshView();
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Impossibile caricare i piani.');
        this.refreshView();
      },
    });
  }

  onPianoChange(): void {
    this.resetSelection('piano');
    this.loadReport();
  }

  loadReport(): void {
    if (!this.selectedPianoId) return;
    this.loading = true;
    this.error = '';
    this.bookingActionError = '';
    this.bookingActionMessage = '';
    this.bookingPendingDeletion = null;
    this.locationPendingDeletion = null;
    this.editingBooking = null;
    this.deletingBookingId = null;
    this.savingBookingId = null;

    this.reportSubscription?.unsubscribe();
    this.reportSubscription = this.api
      .listStanze(this.selectedPianoId)
      .pipe(
        switchMap((stanze) => {
          this.stanze = stanze;
          const seats$ = stanze.length
            ? forkJoin(stanze.map((stanza) => this.api.listPostazioni(stanza.id)))
            : of([] as Postazione[][]);
          return forkJoin({
            seatGroups: seats$,
            bookings: this.api.listBookings(this.reportDate || undefined),
          });
        }),
      )
      .subscribe({
        next: ({ seatGroups, bookings }) => {
          this.postazioni = seatGroups.flat();
          this.bookings = this.filterBookingsForCurrentFloor(bookings);
          this.roomStats = this.buildRoomStats();
          this.loading = false;
          this.refreshView();
        },
        error: (err) => {
          this.loading = false;
          this.error = apiErrorMessage(err, 'Impossibile caricare il riepilogo prenotazioni.');
          this.refreshView();
        },
      });
  }

  get totalSeats(): number {
    return this.postazioni.length;
  }

  get freeSeats(): number {
    return this.postazioni.filter((seat) => seat.stato === 'DISPONIBILE' && !this.isBooked(seat.id)).length;
  }

  get occupiedSeats(): number {
    return this.postazioni.filter((seat) => this.isBooked(seat.id)).length;
  }

  get blockedSeats(): number {
    return this.postazioni.filter((seat) => seat.stato !== 'DISPONIBILE').length;
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

  stateCount(stato: StatoPostazione): number {
    return this.postazioni.filter((seat) => seat.stato === stato).length;
  }

  bookingResourceLabel(booking: Prenotazione): string {
    return booking.risorsaLabel || booking.meetingRoomNome || booking.postazioneCodice || 'Risorsa non disponibile';
  }

  bookingResourceTypeLabel(booking: Prenotazione): string {
    return booking.tipoRisorsaPrenotata === 'MEETING_ROOM' ? 'Sala riunioni' : 'Postazione';
  }

  formatDate(value: string): string {
    const date = new Date(`${value}T00:00:00`);
    return date.toLocaleDateString('it-IT', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  }

  canEditBooking(booking: Prenotazione): boolean {
    return booking.stato === 'CONFERMATA' && booking.dataPrenotazione >= this.minReportDate;
  }

  askCancelBooking(booking: Prenotazione): void {
    if (!this.isAdmin() || this.deletingBookingId !== null || this.savingBookingId !== null) {
      return;
    }

    this.bookingActionError = '';
    this.bookingActionMessage = '';
    this.bookingPendingDeletion = booking;
    this.refreshView();
  }

  closeCancelBookingModal(): void {
    if (this.deletingBookingId !== null) {
      return;
    }

    this.bookingPendingDeletion = null;
    this.refreshView();
  }

  cancelBooking(): void {
    const booking = this.bookingPendingDeletion;
    if (!this.isAdmin() || !booking || this.deletingBookingId !== null) {
      return;
    }

    this.deletingBookingId = booking.id;
    this.bookingActionError = '';
    this.bookingActionMessage = '';

    this.api.cancelBooking(booking.id).subscribe({
      next: () => {
        this.bookings = this.bookings.filter((item) => item.id !== booking.id);
        this.roomStats = this.buildRoomStats();
        this.deletingBookingId = null;
        this.bookingPendingDeletion = null;
        this.bookingActionMessage = 'Prenotazione eliminata.';
        this.refreshView();
      },
      error: (err) => {
        this.deletingBookingId = null;
        this.bookingPendingDeletion = null;
        this.bookingActionError = bookingCancellationErrorMessage(err);
        this.refreshView();
      },
    });
  }

  startEditBooking(booking: Prenotazione): void {
    if (!this.isAdmin() || !this.canEditBooking(booking) || this.deletingBookingId !== null || this.savingBookingId !== null) {
      return;
    }

    this.editingBooking = booking;
    this.editBookingDate = booking.dataPrenotazione;
    this.editStartTime = booking.oraInizio;
    this.editEndTime = booking.oraFine;
    this.bookingActionError = '';
    this.bookingActionMessage = '';
    this.refreshView();
  }

  closeEditBookingModal(): void {
    if (this.savingBookingId !== null) {
      return;
    }

    this.editingBooking = null;
    this.bookingActionError = '';
    this.refreshView();
  }

  availableEditStartTimes(): readonly string[] {
    return this.bookingStartOptions.filter((time) => time < this.editEndTime);
  }

  availableEditEndTimes(): readonly string[] {
    return this.bookingEndOptions.filter((time) => time > this.editStartTime);
  }

  onEditTimeChange(): void {
    this.normalizeEditTimeWindow();
    this.bookingActionError = '';
    this.bookingActionMessage = '';
    this.refreshView();
  }

  saveBookingEdit(): void {
    const booking = this.editingBooking;
    if (!this.isAdmin() || !booking || this.savingBookingId !== null) {
      return;
    }

    const validationError = this.validateEditSelection();
    if (validationError) {
      this.bookingActionError = validationError;
      this.bookingActionMessage = '';
      this.refreshView();
      return;
    }

    this.savingBookingId = booking.id;
    this.bookingActionError = '';
    this.bookingActionMessage = '';

    this.api.updateBooking(booking.id, {
      dataPrenotazione: this.editBookingDate,
      oraInizio: this.editStartTime,
      oraFine: this.editEndTime,
    }).subscribe({
      next: (updatedBooking) => {
        this.bookings = updatedBooking.dataPrenotazione === this.reportDate
          ? this.bookings
            .map((item) => item.id === updatedBooking.id ? { ...item, ...updatedBooking } : item)
            .sort((left, right) => `${left.dataPrenotazione} ${left.oraInizio}`.localeCompare(`${right.dataPrenotazione} ${right.oraInizio}`))
          : this.bookings.filter((item) => item.id !== updatedBooking.id);
        this.roomStats = this.buildRoomStats();
        this.savingBookingId = null;
        this.editingBooking = null;
        this.bookingActionMessage = 'Prenotazione aggiornata.';
        this.refreshView();
      },
      error: (err) => {
        this.savingBookingId = null;
        this.bookingActionError = apiErrorMessage(err, 'Aggiornamento prenotazione non riuscito.');
        this.refreshView();
      },
    });
  }

  canManageRoomSeats(room: RoomStats): boolean {
    return this.canManageDeskStates() && !!room;
  }

  manageRoomButtonLabel(room: RoomStats): string {
    return room.meetingRoom ? 'Gestisci meeting room' : 'Gestisci postazioni';
  }

  roomState(room: RoomStats): StatoPostazione {
    return room.stanza.stato ?? 'DISPONIBILE';
  }

  roomStateLabel(room: RoomStats): string {
    return this.statoLabel(this.roomState(room));
  }

  roomIsNotBookable(room: RoomStats): boolean {
    return this.roomState(room) !== 'DISPONIBILE';
  }

  selectedRoomManagementTitle(): string {
    return this.selectedRoomForState?.meetingRoom ? 'Gestisci meeting room' : 'Stato postazioni';
  }

  selectedRoomManagementSubtitle(): string {
    const room = this.selectedRoomForState;
    if (!room) {
      return '';
    }
    return room.meetingRoom ? `Meeting room: ${room.stanza.nome}` : `Stanza: ${room.stanza.nome}`;
  }

  meetingRoomStateChanged(): boolean {
    const room = this.selectedRoomForState;
    return !!room && this.meetingRoomStateDraft !== this.roomState(room);
  }

  saveMeetingRoomState(): void {
    const room = this.selectedRoomForState;
    if (!room || !room.meetingRoom || !this.canManageDeskStates() || this.updatingMeetingRoomState) {
      return;
    }

    const nextState = this.meetingRoomStateDraft;
    if (nextState === this.roomState(room)) {
      this.seatStateMessage = `Nessuna modifica per ${room.stanza.nome}.`;
      this.seatStateError = '';
      this.refreshView();
      return;
    }

    this.updatingMeetingRoomState = true;
    this.seatStateError = '';
    this.seatStateMessage = '';

    this.api.updateStanzaStato(room.stanza.id, nextState).subscribe({
      next: (updatedRoom) => {
        this.stanze = this.stanze.map((item) => item.id === updatedRoom.id ? updatedRoom : item);
        this.roomStats = this.buildRoomStats();
        this.selectedRoomForState = this.roomStats.find((item) => item.stanza.id === updatedRoom.id) ?? null;
        this.meetingRoomStateDraft = updatedRoom.stato ?? 'DISPONIBILE';
        this.updatingMeetingRoomState = false;
        this.seatStateMessage = `Stato aggiornato per ${updatedRoom.nome}: ${this.statoLabel(this.meetingRoomStateDraft)}.`;
        this.refreshView();
      },
      error: (err) => {
        this.updatingMeetingRoomState = false;
        this.meetingRoomStateDraft = this.roomState(room);
        this.seatStateError = apiErrorMessage(err, 'Aggiornamento stato meeting room non riuscito.');
        this.refreshView();
      },
    });
  }

  selectRoomsView(): void {
    this.activeRoomView = 'rooms';
    this.refreshView();
  }

  selectMeetingRoomsView(): void {
    this.activeRoomView = 'meetingRooms';
    this.refreshView();
  }

  showingRooms(): boolean {
    return this.activeRoomView === 'rooms';
  }

  showingMeetingRooms(): boolean {
    return this.activeRoomView === 'meetingRooms';
  }

  roomCount(): number {
    return this.roomStats.filter((room) => !room.meetingRoom).length;
  }

  meetingRoomCount(): number {
    return this.roomStats.filter((room) => room.meetingRoom).length;
  }

  visibleRoomStats(): RoomStats[] {
    return this.roomStats.filter((room) =>
      this.activeRoomView === 'rooms' ? !room.meetingRoom : room.meetingRoom,
    );
  }

  roomListEmptyMessage(): string {
    if (this.loading) {
      return 'Caricamento riepilogo...';
    }
    return this.activeRoomView === 'rooms'
      ? 'Nessuna stanza configurata.'
      : 'Nessuna meeting room configurata.';
  }

  openSeatStateModal(room: RoomStats): void {
    if (!this.canManageRoomSeats(room)) {
      return;
    }

    this.selectedRoomForState = room;
    this.showSeatStateModal = true;
    this.updatingSeatId = null;
    this.updatingMeetingRoomState = false;
    this.meetingRoomStateDraft = this.roomState(room);
    this.seatStateError = '';
    this.seatStateMessage = '';
    this.seatStateDrafts = Object.fromEntries(
      this.postazioniForRoom(room.stanza.id).map((seat) => [seat.id, seat.stato]),
    );
    this.availableSeatGroups = [];
    this.seatGroupsBySeatId = {};
    this.seatGroupDrafts = Object.fromEntries(
      this.postazioniForRoom(room.stanza.id).map((seat) => [seat.id, null]),
    );
    this.roomGroupDraft = null;
    this.loadingSeatGroups = false;
    this.updatingSeatGroupKey = '';
    this.updatingRoomGroupAction = '';
    this.seatGroupError = '';
    this.seatGroupMessage = '';
    this.refreshView();
    if (!room.meetingRoom) {
      this.loadSeatGroupsForSelectedRoom();
    }
  }

  closeSeatStateModal(): void {
    if (this.updatingSeatId !== null || this.updatingMeetingRoomState || !!this.updatingSeatGroupKey || !!this.updatingRoomGroupAction) {
      return;
    }

    this.showSeatStateModal = false;
    this.selectedRoomForState = null;
    this.updatingMeetingRoomState = false;
    this.meetingRoomStateDraft = 'DISPONIBILE';
    this.seatStateError = '';
    this.seatStateMessage = '';
    this.seatStateDrafts = {};
    this.availableSeatGroups = [];
    this.seatGroupsBySeatId = {};
    this.seatGroupDrafts = {};
    this.roomGroupDraft = null;
    this.loadingSeatGroups = false;
    this.updatingSeatGroupKey = '';
    this.updatingRoomGroupAction = '';
    this.seatGroupError = '';
    this.seatGroupMessage = '';
    this.refreshView();
  }

  postazioniForRoom(stanzaId: number): Postazione[] {
    return this.postazioni
      .filter((seat) => seat.stanzaId === stanzaId)
      .sort((left, right) => left.codice.localeCompare(right.codice));
  }

  statoOptions(): readonly StatoPostazione[] {
    return ['DISPONIBILE', 'NON_DISPONIBILE', 'MANUTENZIONE', 'CAMBIO_DESTINAZIONE'];
  }

  statoLabel(stato: StatoPostazione): string {
    switch (stato) {
      case 'DISPONIBILE':
        return 'Disponibile';
      case 'NON_DISPONIBILE':
        return 'Non disponibile';
      case 'MANUTENZIONE':
        return 'Manutenzione';
      case 'CAMBIO_DESTINAZIONE':
        return 'Cambio destinazione';
    }
  }

  isSeatBookedToday(postazioneId: number): boolean {
    return this.isBooked(postazioneId);
  }

  saveSeatState(seat: Postazione): void {
    if (!this.canManageDeskStates() || this.updatingSeatId !== null) {
      return;
    }

    const nextState = this.seatStateDrafts[seat.id] ?? seat.stato;
    if (nextState === seat.stato) {
      this.seatStateMessage = `Nessuna modifica per ${seat.codice}.`;
      this.seatStateError = '';
      this.refreshView();
      return;
    }

    this.updatingSeatId = seat.id;
    this.seatStateError = '';
    this.seatStateMessage = '';

    this.api.updatePostazioneStato(seat.id, nextState).subscribe({
      next: (updatedSeat) => {
        this.postazioni = this.postazioni.map((item) => item.id === updatedSeat.id ? updatedSeat : item);
        this.seatStateDrafts[seat.id] = updatedSeat.stato;
        this.refreshRoomStatsAfterSeatUpdate();
        this.updatingSeatId = null;
        this.seatStateMessage = `Stato aggiornato per ${updatedSeat.codice}: ${this.statoLabel(updatedSeat.stato)}.`;
        this.refreshView();
      },
      error: (err) => {
        this.updatingSeatId = null;
        this.seatStateDrafts[seat.id] = seat.stato;
        this.seatStateError = apiErrorMessage(err, 'Aggiornamento stato postazione non riuscito.');
        this.refreshView();
      },
    });
  }

  seatGroupsForSeat(seatId: number): Gruppo[] {
    const assignedIds = new Set((this.seatGroupsBySeatId[seatId] ?? []).map((item) => item.gruppoId));
    return this.availableSeatGroups.filter((group) => assignedIds.has(group.id));
  }

  roomGroupsSummary(): Array<{ group: Gruppo; assignedCount: number; totalSeats: number }> {
    const seats = this.selectedRoomForState ? this.postazioniForRoom(this.selectedRoomForState.stanza.id) : [];
    const totalSeats = seats.length;
    return this.availableSeatGroups
      .map((group) => ({
        group,
        assignedCount: seats.filter((seat) => this.hasSeatGroup(seat.id, group.id)).length,
        totalSeats,
      }))
      .filter((item) => item.assignedCount > 0);
  }

  availableGroupsForRoom(): Gruppo[] {
    return this.availableSeatGroups;
  }

  availableGroupsForSeat(seatId: number): Gruppo[] {
    const assignedIds = new Set((this.seatGroupsBySeatId[seatId] ?? []).map((item) => item.gruppoId));
    return this.availableSeatGroups.filter((group) => !assignedIds.has(group.id));
  }

  assignGroupToSeat(seat: Postazione): void {
    const groupId = this.seatGroupDrafts[seat.id];
    if (!groupId || this.updatingSeatGroupKey) {
      return;
    }

    this.updatingSeatGroupKey = `add-${seat.id}-${groupId}`;
    this.seatGroupError = '';
    this.seatGroupMessage = '';

    this.api.addGroupToSeat(groupId, seat.id).subscribe({
      next: () => {
        this.seatGroupDrafts[seat.id] = null;
        this.seatGroupMessage = `Gruppo assegnato a ${seat.codice}.`;
        this.updatingSeatGroupKey = '';
        this.loadSeatGroupAssignmentsForCurrentRoom();
      },
      error: (err) => {
        this.updatingSeatGroupKey = '';
        this.seatGroupError = apiErrorMessage(err, 'Associazione gruppo-postazione non riuscita.');
        this.refreshView();
      },
    });
  }

  removeGroupFromSeat(seat: Postazione, group: Gruppo): void {
    if (this.updatingSeatGroupKey) {
      return;
    }

    this.updatingSeatGroupKey = `remove-${seat.id}-${group.id}`;
    this.seatGroupError = '';
    this.seatGroupMessage = '';

    this.api.removeGroupFromSeat(group.id, seat.id).subscribe({
      next: () => {
        this.seatGroupMessage = `Gruppo rimosso da ${seat.codice}.`;
        this.updatingSeatGroupKey = '';
        this.loadSeatGroupAssignmentsForCurrentRoom();
      },
      error: (err) => {
        this.updatingSeatGroupKey = '';
        this.seatGroupError = apiErrorMessage(err, 'Rimozione gruppo-postazione non riuscita.');
        this.refreshView();
      },
    });
  }

  seatGroupBusy(action: 'add' | 'remove', seatId: number, groupId: number): boolean {
    return this.updatingSeatGroupKey === `${action}-${seatId}-${groupId}`;
  }

  roomGroupBusy(action: 'add' | 'remove', groupId: number): boolean {
    return this.updatingRoomGroupAction === `${action}-${groupId}`;
  }

  applyGroupToRoom(): void {
    const room = this.selectedRoomForState;
    const groupId = this.roomGroupDraft;
    if (!room || !groupId || this.updatingSeatGroupKey || this.updatingRoomGroupAction) {
      return;
    }

    const seatsToUpdate = this.postazioniForRoom(room.stanza.id)
      .filter((seat) => !this.hasSeatGroup(seat.id, groupId));

    if (!seatsToUpdate.length) {
      this.seatGroupMessage = 'Tutte le postazioni della stanza hanno gia\' questo gruppo.';
      this.seatGroupError = '';
      this.refreshView();
      return;
    }

    this.updatingRoomGroupAction = `add-${groupId}`;
    this.seatGroupError = '';
    this.seatGroupMessage = '';

    forkJoin(seatsToUpdate.map((seat) => this.api.addGroupToSeat(groupId, seat.id))).subscribe({
      next: () => {
        this.roomGroupDraft = null;
        this.seatGroupMessage = `Gruppo assegnato a ${seatsToUpdate.length} postazioni della stanza.`;
        this.updatingRoomGroupAction = '';
        this.loadSeatGroupAssignmentsForCurrentRoom();
      },
      error: (err) => {
        this.updatingRoomGroupAction = '';
        this.seatGroupError = apiErrorMessage(err, 'Associazione gruppo alla stanza non riuscita.');
        this.refreshView();
      },
    });
  }

  removeGroupFromRoom(group: Gruppo): void {
    const room = this.selectedRoomForState;
    if (!room || this.updatingSeatGroupKey || this.updatingRoomGroupAction) {
      return;
    }

    const seatsToUpdate = this.postazioniForRoom(room.stanza.id)
      .filter((seat) => this.hasSeatGroup(seat.id, group.id));

    if (!seatsToUpdate.length) {
      this.seatGroupMessage = 'Nessuna postazione della stanza ha questo gruppo.';
      this.seatGroupError = '';
      this.refreshView();
      return;
    }

    this.updatingRoomGroupAction = `remove-${group.id}`;
    this.seatGroupError = '';
    this.seatGroupMessage = '';

    forkJoin(seatsToUpdate.map((seat) => this.api.removeGroupFromSeat(group.id, seat.id))).subscribe({
      next: () => {
        this.seatGroupMessage = `Gruppo rimosso da ${seatsToUpdate.length} postazioni della stanza.`;
        this.updatingRoomGroupAction = '';
        this.loadSeatGroupAssignmentsForCurrentRoom();
      },
      error: (err) => {
        this.updatingRoomGroupAction = '';
        this.seatGroupError = apiErrorMessage(err, 'Rimozione gruppo dalla stanza non riuscita.');
        this.refreshView();
      },
    });
  }

  private resetSelection(level: 'sede' | 'edificio' | 'piano'): void {
    this.error = '';
    if (level === 'sede') {
      this.edifici = [];
      this.selectedEdificioId = null;
      this.edificiSubscription?.unsubscribe();
    }
    if (level === 'sede' || level === 'edificio') {
      this.piani = [];
      this.selectedPianoId = null;
      this.pianiSubscription?.unsubscribe();
    }
    this.reportSubscription?.unsubscribe();
    this.stanze = [];
    this.postazioni = [];
    this.bookings = [];
    this.roomStats = [];
    this.bookingActionError = '';
    this.bookingActionMessage = '';
    this.bookingPendingDeletion = null;
    this.locationPendingDeletion = null;
    this.editingBooking = null;
    this.deletingBookingId = null;
    this.savingBookingId = null;
    this.availableSeatGroups = [];
    this.seatGroupsBySeatId = {};
    this.seatGroupDrafts = {};
    this.roomGroupDraft = null;
    this.refreshView();
  }

  private filterBookingsForCurrentFloor(bookings: Prenotazione[]): Prenotazione[] {
    const stationIds = new Set(this.postazioni.map((seat) => seat.id));
    const meetingRoomIds = new Set(this.stanze.filter((stanza) => stanza.tipo === 'MEETING_ROOM').map((stanza) => stanza.id));
    return bookings.filter((booking) =>
      booking.stato === 'CONFERMATA'
      && (
        (booking.postazioneId != null && stationIds.has(booking.postazioneId))
        || (booking.meetingRoomStanzaId != null && meetingRoomIds.has(booking.meetingRoomStanzaId))
      ),
    );
  }

  private buildRoomStats(): RoomStats[] {
    const bookedIds = new Set(
      this.bookings
        .filter((booking) => booking.stato === 'CONFERMATA' && booking.postazioneId != null)
        .map((booking) => booking.postazioneId as number),
    );

    return this.stanze.map((stanza) => {
      const meetingRoomSlots = this.bookings
        .filter((booking) => booking.meetingRoomStanzaId === stanza.id)
        .map((booking) => `${booking.oraInizio}-${booking.oraFine}`)
        .sort();
      const seats = this.postazioni.filter((seat) => seat.stanzaId === stanza.id);
      const freeSeats = seats.filter((seat) => seat.stato === 'DISPONIBILE' && !bookedIds.has(seat.id));
      const occupiedSeats = seats.filter((seat) => bookedIds.has(seat.id));
      const blockedSeats = seats.filter((seat) => seat.stato !== 'DISPONIBILE');
      return {
        stanza,
        meetingRoom: stanza.tipo === 'MEETING_ROOM',
        total: seats.length,
        free: freeSeats.length,
        occupied: occupiedSeats.length,
        unavailable: seats.filter((seat) => seat.stato === 'NON_DISPONIBILE' || seat.stato === 'CAMBIO_DESTINAZIONE').length,
        maintenance: seats.filter((seat) => seat.stato === 'MANUTENZIONE').length,
        meetingRoomSlots,
        freeCodes: freeSeats.map((seat) => seat.codice),
        occupiedCodes: occupiedSeats.map((seat) => seat.codice),
        blockedCodes: blockedSeats.map((seat) => seat.codice),
      };
    });
  }

  private isBooked(postazioneId: number): boolean {
    return this.bookings.some((booking) => booking.postazioneId === postazioneId && booking.stato === 'CONFERMATA');
  }

  private refreshRoomStatsAfterSeatUpdate(): void {
    this.roomStats = this.buildRoomStats();
    if (this.selectedRoomForState) {
      this.selectedRoomForState = this.roomStats.find((room) => room.stanza.id === this.selectedRoomForState?.stanza.id) ?? null;
    }
  }

  private loadSeatGroupsForSelectedRoom(): void {
    const room = this.selectedRoomForState;
    if (!room) {
      return;
    }

    const seats = this.postazioniForRoom(room.stanza.id);
    this.loadingSeatGroups = true;
    this.seatGroupError = '';

    forkJoin({
      groups: this.api.listGroups(),
      assignments: seats.length
        ? forkJoin(seats.map((seat) => this.api.listSeatGroups(seat.id)))
        : of([] as GruppoPostazione[][]),
    }).subscribe({
      next: ({ groups, assignments }) => {
        this.availableSeatGroups = groups;
        this.seatGroupsBySeatId = Object.fromEntries(
          seats.map((seat, index) => [seat.id, assignments[index] ?? []]),
        );
        this.roomGroupDraft = null;
        this.loadingSeatGroups = false;
        this.refreshView();
      },
      error: (err) => {
        this.loadingSeatGroups = false;
        this.seatGroupError = apiErrorMessage(err, 'Impossibile caricare i gruppi associati alle postazioni.');
        this.refreshView();
      },
    });
  }

  private loadSeatGroupAssignmentsForCurrentRoom(): void {
    const room = this.selectedRoomForState;
    if (!room) {
      return;
    }

    const seats = this.postazioniForRoom(room.stanza.id);
    this.loadingSeatGroups = true;

    (seats.length
      ? forkJoin(seats.map((seat) => this.api.listSeatGroups(seat.id)))
      : of([] as GruppoPostazione[][])
    ).subscribe({
      next: (assignments) => {
        this.seatGroupsBySeatId = Object.fromEntries(
          seats.map((seat, index) => [seat.id, assignments[index] ?? []]),
        );
        this.roomGroupDraft = null;
        this.loadingSeatGroups = false;
        this.refreshView();
      },
      error: (err) => {
        this.loadingSeatGroups = false;
        this.seatGroupError = apiErrorMessage(err, 'Impossibile aggiornare i gruppi delle postazioni.');
        this.refreshView();
      },
    });
  }

  private buildEmptySedeForm(): CreateSedeForm {
    return {
      nome: '',
      citta: '',
      indirizzo: '',
      latitudine: '',
      longitudine: '',
    };
  }

  private buildEmptyPianoForm(): CreatePianoForm {
    return {
      nome: '',
      numero: '',
    };
  }

  private getNextPianoNumero(): number {
    return this.piani.length
      ? Math.max(...this.piani.map((piano) => piano.numero)) + 1
      : 0;
  }

  private withExpriviaPrefix(value: string): string {
    const normalized = value.trim().replace(/\s+/g, ' ');
    const withoutPrefix = normalized.replace(/^exprivia\s*[-:]?\s*/i, '').trim();
    return withoutPrefix ? `Exprivia - ${withoutPrefix}` : 'Exprivia';
  }

  private parseCoordinate(value: string, label: 'Latitudine' | 'Longitudine'): { value: number | null; error: string | null } {
    const normalized = value.replace(',', '.').trim();
    if (!normalized) {
      return { value: null, error: null };
    }

    const parsed = Number(normalized);
    if (Number.isNaN(parsed)) {
      return { value: null, error: `${label} non valida. Usa un numero, ad esempio 41.9028.` };
    }

    return { value: parsed, error: null };
  }

  private locationDeletionErrorMessage(type: LocationDeletionTarget['type']): string {
    switch (type) {
      case 'sede':
        return 'Eliminazione sede non riuscita.';
      case 'edificio':
        return 'Eliminazione edificio non riuscita.';
      case 'piano':
        return 'Eliminazione piano non riuscita.';
    }
  }

  private hasSeatGroup(seatId: number, groupId: number): boolean {
    return (this.seatGroupsBySeatId[seatId] ?? []).some((item) => item.gruppoId === groupId);
  }

  private refreshView(): void {
    this.cdr.detectChanges();
  }

  private validateEditSelection(): string | null {
    if (!this.editBookingDate) {
      return 'Seleziona una data valida.';
    }
    if (this.editBookingDate < this.minReportDate) {
      return 'Le prenotazioni sono modificabili solo a partire dal primo giorno lavorativo disponibile.';
    }
    if (isWeekendIsoDate(this.editBookingDate)) {
      return 'Le prenotazioni non sono consentite il sabato e la domenica.';
    }
    if (!this.editStartTime || !this.editEndTime) {
      return 'Seleziona una fascia oraria valida.';
    }
    if (!isWithinBookingWindow(this.editStartTime, this.editEndTime)) {
      if (this.editStartTime >= this.editEndTime) {
        return "L'ora di inizio deve essere precedente all'ora di fine.";
      }
      return 'Le prenotazioni sono consentite solo tra le 09:00 e le 18:00.';
    }
    return null;
  }

  private normalizeEditTimeWindow(): void {
    if (this.editStartTime < BOOKING_DAY_START) {
      this.editStartTime = BOOKING_DAY_START;
    }
    if (this.editEndTime > BOOKING_DAY_END) {
      this.editEndTime = BOOKING_DAY_END;
    }
    if (this.editStartTime >= this.editEndTime) {
      this.editEndTime = nextBookingTimeOption(this.editStartTime) ?? BOOKING_DAY_END;
    }
  }
}
