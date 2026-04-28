import { ChangeDetectorRef, Component, computed, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { DashboardPrenotazione } from '../../core/app.models';
import { apiErrorMessage } from '../../core/api-error.utils';
import { AuthService } from '../../core/auth.service';
import { isWeekendIsoDate, nextBookableIsoDate } from '../../core/date.utils';
import { BOOKING_ROLES, PLAN_EDITOR_ROLES } from '../../core/role-access';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, FormsModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class DashboardComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly user = this.auth.currentUser;
  bookings: DashboardPrenotazione[] = [];
  bookingsLoading = false;
  bookingsError = '';
  deletingBookingId: number | null = null;
  editingBookingId: number | null = null;
  savingBookingId: number | null = null;
  editBookingDate = '';
  editStartTime = '09:00';
  editEndTime = '18:00';
  editError = '';
  editMessage = '';
  readonly minBookingDate = nextBookableIsoDate();

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
  protected readonly canAccessPlanEditor = computed(() => this.auth.hasAnyRole(PLAN_EDITOR_ROLES));
  protected readonly isReception = computed(() => this.auth.hasAnyRole(['RECEPTION']));
  protected readonly canBook = computed(() =>
    this.auth.hasAnyRole(BOOKING_ROLES),
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

  cancelBooking(booking: DashboardPrenotazione): void {
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
        this.bookingsError = apiErrorMessage(err, 'Eliminazione prenotazione non riuscita.');
        this.cdr.detectChanges();
      },
    });
  }

  startEdit(booking: DashboardPrenotazione): void {
    this.editingBookingId = booking.id;
    this.editBookingDate = booking.dataPrenotazione;
    this.editStartTime = booking.oraInizio;
    this.editEndTime = booking.oraFine;
    this.editError = '';
    this.editMessage = '';
    this.cdr.detectChanges();
  }

  cancelEdit(): void {
    this.editingBookingId = null;
    this.editError = '';
    this.editMessage = '';
    this.cdr.detectChanges();
  }

  canEditBooking(booking: DashboardPrenotazione): boolean {
    return booking.dataPrenotazione >= this.minBookingDate;
  }

  saveEdit(booking: DashboardPrenotazione): void {
    const validationError = this.validateEditSelection();
    if (validationError) {
      this.editError = validationError;
      this.editMessage = '';
      this.cdr.detectChanges();
      return;
    }

    this.savingBookingId = booking.id;
    this.editError = '';
    this.editMessage = '';
    this.api.updateBooking(booking.id, {
      dataPrenotazione: this.editBookingDate,
      oraInizio: this.editStartTime,
      oraFine: this.editEndTime,
    }).subscribe({
      next: (updated) => {
        this.bookings = this.bookings
          .map((item) => item.id === booking.id ? { ...item, ...updated } : item)
          .sort((a, b) => `${a.dataPrenotazione} ${a.oraInizio}`.localeCompare(`${b.dataPrenotazione} ${b.oraInizio}`));
        this.savingBookingId = null;
        this.editingBookingId = null;
        this.editMessage = 'Prenotazione aggiornata.';
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.savingBookingId = null;
        this.editError = apiErrorMessage(err, 'Aggiornamento prenotazione non riuscito.');
        this.cdr.detectChanges();
      },
    });
  }

  private loadBookings(): void {
    this.bookingsLoading = true;
    this.bookingsError = '';

    this.api.listMyDashboardBookings().subscribe({
      next: (bookings) => {
        this.bookings = bookings
          .filter((booking) => booking.stato === 'CONFERMATA')
          .sort((a, b) =>
            `${a.dataPrenotazione} ${a.oraInizio}`.localeCompare(`${b.dataPrenotazione} ${b.oraInizio}`),
          )
          .map((booking) => ({
            ...booking,
            sedeLabel: booking.sedeLabel || 'Sede non disponibile',
            pianoLabel: booking.pianoLabel || 'Piano non disponibile',
          }));
        this.bookingsLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.bookings = [];
        this.bookingsLoading = false;
        this.bookingsError = apiErrorMessage(err, 'Impossibile caricare le prenotazioni.');
        this.cdr.detectChanges();
      },
    });
  }

  private validateEditSelection(): string | null {
    if (!this.editBookingDate) {
      return 'Seleziona una data valida.';
    }
    if (this.editBookingDate < this.minBookingDate) {
      return 'Le prenotazioni sono modificabili solo a partire dal primo giorno lavorativo disponibile.';
    }
    if (isWeekendIsoDate(this.editBookingDate)) {
      return 'Le prenotazioni non sono consentite il sabato e la domenica.';
    }
    if (!this.editStartTime || !this.editEndTime || this.editStartTime >= this.editEndTime) {
      return "L'ora di inizio deve essere precedente all'ora di fine.";
    }
    return null;
  }
}
