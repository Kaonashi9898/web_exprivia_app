import { ChangeDetectorRef, Component, computed, inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, of, Subscription, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Edificio, Piano, Postazione, Prenotazione, Sede, Stanza, StatoPostazione } from '../../core/app.models';
import { apiErrorMessage } from '../../core/api-error.utils';
import { AuthService } from '../../core/auth.service';
import { nextBookableIsoDate } from '../../core/date.utils';

interface RoomStats {
  stanza: Stanza;
  total: number;
  free: number;
  occupied: number;
  unavailable: number;
  maintenance: number;
  freeCodes: string[];
  occupiedCodes: string[];
  blockedCodes: string[];
}

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

@Component({
  selector: 'app-bookings',
  imports: [FormsModule],
  templateUrl: './bookings.html',
  styleUrl: './bookings.css',
})
export class BookingsComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private sediSubscription: Subscription | null = null;
  private edificiSubscription: Subscription | null = null;
  private pianiSubscription: Subscription | null = null;
  private reportSubscription: Subscription | null = null;

  protected readonly isAdmin = computed(() => this.auth.hasAnyRole(['ADMIN']));

  sedi: Sede[] = [];
  edifici: Edificio[] = [];
  piani: Piano[] = [];
  stanze: Stanza[] = [];
  postazioni: Postazione[] = [];
  bookings: Prenotazione[] = [];
  roomStats: RoomStats[] = [];

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
    if (!confirm(`Eliminare la sede ${sede?.nome ?? ''}? Verranno rimossi anche edifici, piani e postazioni collegati.`)) {
      return;
    }

    const deletedId = this.selectedSedeId;
    this.api.deleteSede(deletedId).subscribe({
      next: () => {
        this.sedi = this.sedi.filter((item) => item.id !== deletedId);
        this.selectedSedeId = null;
        this.resetSelection('sede');
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Eliminazione sede non riuscita.');
        this.refreshView();
      },
    });
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
    if (!confirm(`Eliminare l'edificio ${edificio?.nome ?? ''}? Verranno rimossi anche piani e postazioni collegati.`)) {
      return;
    }

    const deletedId = this.selectedEdificioId;
    this.api.deleteEdificio(deletedId).subscribe({
      next: () => {
        this.edifici = this.edifici.filter((item) => item.id !== deletedId);
        this.selectedEdificioId = null;
        this.resetSelection('edificio');
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Eliminazione edificio non riuscita.');
        this.refreshView();
      },
    });
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

    if (!confirm(`Eliminare ${pianoLabel}? Verranno rimosse anche stanza, postazioni e planimetria collegate.`)) {
      return;
    }

    const deletedId = this.selectedPianoId;
    this.api.deletePiano(deletedId).subscribe({
      next: () => {
        this.piani = this.piani.filter((item) => item.id !== deletedId);
        this.selectedPianoId = null;
        this.resetSelection('piano');
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Eliminazione piano non riuscita.');
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
    this.refreshView();
  }

  private filterBookingsForCurrentFloor(bookings: Prenotazione[]): Prenotazione[] {
    const stationIds = new Set(this.postazioni.map((seat) => seat.id));
    return bookings.filter((booking) =>
      booking.stato === 'CONFERMATA' && stationIds.has(booking.postazioneId),
    );
  }

  private buildRoomStats(): RoomStats[] {
    const bookedIds = new Set(this.bookings.filter((booking) => booking.stato === 'CONFERMATA').map((booking) => booking.postazioneId));

    return this.stanze.map((stanza) => {
      const seats = this.postazioni.filter((seat) => seat.stanzaId === stanza.id);
      const freeSeats = seats.filter((seat) => seat.stato === 'DISPONIBILE' && !bookedIds.has(seat.id));
      const occupiedSeats = seats.filter((seat) => bookedIds.has(seat.id));
      const blockedSeats = seats.filter((seat) => seat.stato !== 'DISPONIBILE');
      return {
        stanza,
        total: seats.length,
        free: freeSeats.length,
        occupied: occupiedSeats.length,
        unavailable: seats.filter((seat) => seat.stato === 'NON_DISPONIBILE' || seat.stato === 'CAMBIO_DESTINAZIONE').length,
        maintenance: seats.filter((seat) => seat.stato === 'MANUTENZIONE').length,
        freeCodes: freeSeats.map((seat) => seat.codice),
        occupiedCodes: occupiedSeats.map((seat) => seat.codice),
        blockedCodes: blockedSeats.map((seat) => seat.codice),
      };
    });
  }

  private isBooked(postazioneId: number): boolean {
    return this.bookings.some((booking) => booking.postazioneId === postazioneId && booking.stato === 'CONFERMATA');
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

  private refreshView(): void {
    this.cdr.detectChanges();
  }
}
