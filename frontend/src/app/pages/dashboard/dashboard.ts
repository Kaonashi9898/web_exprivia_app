import { ChangeDetectorRef, Component, computed, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, map, of, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Prenotazione } from '../../core/app.models';
import { AuthService } from '../../core/auth.service';

interface DashboardBooking extends Prenotazione {
  sedeLabel: string;
  pianoLabel: string;
}

interface StanzaLocationInfo {
  sedeLabel: string;
  pianoLabel: string;
}

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class DashboardComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly user = this.auth.currentUser;
  bookings: DashboardBooking[] = [];
  bookingsLoading = false;
  bookingsError = '';
  deletingBookingId: number | null = null;

  protected readonly roleLabel = computed(() => {
    const role = this.auth.ruolo();
    const labels = {
      ADMIN: 'Amministratore',
      BUILDING_MANAGER: 'Building manager',
      RECEPTION: 'Receptionist',
      USER: 'Dipendente',
      GUEST: 'Guest',
    };
    return role ? labels[role] : '';
  });
  protected readonly isAdmin = computed(() => this.auth.hasAnyRole(['ADMIN']));
  protected readonly isBuildingManager = computed(() => this.auth.hasAnyRole(['ADMIN', 'BUILDING_MANAGER']));
  protected readonly isReception = computed(() => this.auth.hasAnyRole(['RECEPTION']));
  protected readonly canBook = computed(() =>
    this.auth.hasAnyRole(['ADMIN', 'RECEPTION', 'USER']),
  );

  ngOnInit(): void {
    this.loadBookings();
  }

  formatDate(value: string): string {
    const date = new Date(`${value}T00:00:00`);
    return date.toLocaleDateString('it-IT', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  }

  cancelBooking(booking: DashboardBooking): void {
    if (this.deletingBookingId || !confirm(`Eliminare la prenotazione per ${booking.postazioneCodice}?`)) {
      return;
    }

    this.deletingBookingId = booking.id;
    this.bookingsError = '';
    this.api.cancelBooking(booking.id).subscribe({
      next: () => {
        this.bookings = this.bookings.filter((item) => item.id !== booking.id);
        this.deletingBookingId = null;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.deletingBookingId = null;
        this.bookingsError = err?.error?.message ?? 'Eliminazione prenotazione non riuscita.';
        this.cdr.detectChanges();
      },
    });
  }

  private loadBookings(): void {
    this.bookingsLoading = true;
    this.bookingsError = '';

    forkJoin({
      bookings: this.api.listMyBookings(),
      stanzaToLocation: this.loadStanzaToLocationMap(),
    }).subscribe({
      next: ({ bookings, stanzaToLocation }) => {
        this.bookings = bookings
          .filter((booking) => booking.stato === 'CONFERMATA')
          .sort((a, b) =>
            `${a.dataPrenotazione} ${a.oraInizio}`.localeCompare(`${b.dataPrenotazione} ${b.oraInizio}`),
          )
          .map((booking) => {
            const location = stanzaToLocation.get(booking.stanzaId);
            return {
              ...booking,
              sedeLabel: location?.sedeLabel ?? 'Sede non disponibile',
              pianoLabel: location?.pianoLabel ?? 'Piano non disponibile',
            };
          });
        this.bookingsLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.bookings = [];
        this.bookingsLoading = false;
        this.bookingsError = err?.error?.message ?? 'Impossibile caricare le prenotazioni.';
        this.cdr.detectChanges();
      },
    });
  }

  private loadStanzaToLocationMap() {
    return this.api.listSedi().pipe(
      switchMap((sedi) => {
        if (!sedi.length) {
          return of(new Map<number, StanzaLocationInfo>());
        }

        return forkJoin(
          sedi.map((sede) =>
            this.api.listEdifici(sede.id).pipe(
              switchMap((edifici) =>
                edifici.length
                  ? forkJoin(
                      edifici.map((edificio) =>
                        this.api.listPiani(edificio.id).pipe(
                          switchMap((piani) =>
                            piani.length
                              ? forkJoin(
                                  piani.map((piano) =>
                                    this.api.listStanze(piano.id).pipe(
                                      map((stanze) =>
                                        stanze.map((stanza) => ({
                                          stanzaId: stanza.id,
                                          sedeLabel: `${sede.nome} - ${sede.citta}`,
                                          pianoLabel: piano.nome?.trim() || this.getPianoLabel(piano.numero),
                                        })),
                                      ),
                                    ),
                                  ),
                                )
                              : of([]),
                          ),
                        ),
                      ),
                    )
                  : of([]),
              ),
            ),
          ),
        ).pipe(
          map((groups) => {
            const mapping = new Map<number, StanzaLocationInfo>();
            groups.flat(3).forEach((item) =>
              mapping.set(item.stanzaId, {
                sedeLabel: item.sedeLabel,
                pianoLabel: item.pianoLabel,
              }),
            );
            return mapping;
          }),
        );
      }),
    );
  }

  private getPianoLabel(numero: number): string {
    if (numero === 0) return 'Piano terra';
    if (numero === 1) return 'Primo piano';
    if (numero === 2) return 'Secondo piano';
    return `Piano ${numero}`;
  }
}
