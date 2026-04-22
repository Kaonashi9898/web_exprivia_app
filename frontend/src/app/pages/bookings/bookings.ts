import { ChangeDetectorRef, Component, computed, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, of, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Edificio, Piano, Postazione, Prenotazione, Sede, Stanza, StatoPostazione } from '../../core/app.models';
import { AuthService } from '../../core/auth.service';

const EXPRIVIA_ITALIA_SEDI: Sede[] = [
  { id: 1, nome: 'Exprivia - Roma Bufalotta', indirizzo: 'Via della Bufalotta 378', citta: 'Roma' },
  { id: 2, nome: 'Exprivia - Molfetta Headquarter', indirizzo: 'Via A. Olivetti 11', citta: 'Molfetta' },
  { id: 3, nome: 'Exprivia - Molfetta Agnelli', indirizzo: 'Via Giovanni Agnelli 5', citta: 'Molfetta' },
  { id: 4, nome: 'Exprivia - Milano', indirizzo: 'Via dei Valtorta 43', citta: 'Milano' },
  { id: 5, nome: 'Exprivia - Lecce', indirizzo: 'Campus Ecotekne, Via Monteroni 165', citta: 'Lecce' },
  { id: 6, nome: 'Exprivia - Matera', indirizzo: 'Via Giovanni Agnelli snc', citta: 'Matera' },
  { id: 7, nome: 'Exprivia - Palermo', indirizzo: 'Viale Regione Siciliana Nord-Ovest 7275', citta: 'Palermo' },
  { id: 8, nome: 'Exprivia - Trento', indirizzo: 'Via Alcide De Gasperi 77', citta: 'Trento' },
  { id: 9, nome: 'Exprivia - Vicenza', indirizzo: 'Via L. Lazzaro Zamenhof 817', citta: 'Vicenza' },
];

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

@Component({
  selector: 'app-bookings',
  imports: [FormsModule],
  templateUrl: './bookings.html',
  styleUrl: './bookings.css',
})
export class BookingsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly isAdmin = computed(() => this.auth.hasAnyRole(['ADMIN']));

  sedi: Sede[] = EXPRIVIA_ITALIA_SEDI;
  edifici: Edificio[] = [];
  piani: Piano[] = [];
  stanze: Stanza[] = [];
  postazioni: Postazione[] = [];
  bookings: Prenotazione[] = [];
  roomStats: RoomStats[] = [];

  selectedSedeId: number | null = null;
  selectedEdificioId: number | null = null;
  selectedPianoId: number | null = null;
  reportDate = new Date().toISOString().slice(0, 10);
  loading = false;
  error = '';

  ngOnInit(): void {
    this.loadSedi();
  }

  loadSedi(): void {
    this.api.listSedi().subscribe({
      next: (sedi) => {
        this.sedi = sedi.length ? sedi : EXPRIVIA_ITALIA_SEDI;
        this.refreshView();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Impossibile caricare le sedi.';
        this.refreshView();
      },
    });
  }

  createSede(): void {
    const nome = prompt('Nome della nuova sede');
    if (!nome?.trim()) return;
    const citta = prompt('Citta della nuova sede');
    if (!citta?.trim()) return;
    const indirizzo = prompt('Indirizzo della nuova sede');
    if (!indirizzo?.trim()) return;

    this.api.createSede({
      nome: nome.trim(),
      citta: citta.trim(),
      indirizzo: indirizzo.trim(),
      latitudine: null,
      longitudine: null,
    }).subscribe({
      next: (sede) => {
        this.sedi = [...this.sedi.filter((item) => item.id !== sede.id), sede];
        this.selectedSedeId = sede.id;
        this.onSedeChange();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Creazione sede non riuscita.';
        this.refreshView();
      },
    });
  }

  deleteSelectedSede(): void {
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
        this.error = err?.error?.message ?? 'Eliminazione sede non riuscita.';
        this.refreshView();
      },
    });
  }

  createEdificio(): void {
    if (!this.selectedSedeId) return;
    const nome = prompt('Nome del nuovo edificio');
    if (!nome?.trim()) return;

    this.api.createEdificio({ nome: nome.trim(), sedeId: this.selectedSedeId }).subscribe({
      next: (edificio) => {
        this.edifici = [...this.edifici.filter((item) => item.id !== edificio.id), edificio];
        this.selectedEdificioId = edificio.id;
        this.onEdificioChange();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Creazione edificio non riuscita.';
        this.refreshView();
      },
    });
  }

  deleteSelectedEdificio(): void {
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
        this.error = err?.error?.message ?? 'Eliminazione edificio non riuscita.';
        this.refreshView();
      },
    });
  }

  onSedeChange(): void {
    this.resetSelection('sede');
    if (!this.selectedSedeId) return;

    this.api.listEdifici(this.selectedSedeId).subscribe({
      next: (edifici) => {
        this.edifici = edifici;
        this.refreshView();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Impossibile caricare gli edifici.';
        this.refreshView();
      },
    });
  }

  onEdificioChange(): void {
    this.resetSelection('edificio');
    if (!this.selectedEdificioId) return;

    this.api.listPiani(this.selectedEdificioId).subscribe({
      next: (piani) => {
        this.piani = piani.filter((piano) => this.hasPianoName(piano));
        this.refreshView();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Impossibile caricare i piani.';
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

    this.api
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
          this.error = err?.error?.message ?? 'Impossibile caricare il riepilogo prenotazioni.';
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

  hasPianoName(piano: Piano): boolean {
    return !!piano.nome?.trim();
  }

  stateCount(stato: StatoPostazione): number {
    return this.postazioni.filter((seat) => seat.stato === stato).length;
  }

  private resetSelection(level: 'sede' | 'edificio' | 'piano'): void {
    this.error = '';
    if (level === 'sede') {
      this.edifici = [];
      this.selectedEdificioId = null;
    }
    if (level === 'sede' || level === 'edificio') {
      this.piani = [];
      this.selectedPianoId = null;
    }
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

  private refreshView(): void {
    this.cdr.detectChanges();
  }
}
