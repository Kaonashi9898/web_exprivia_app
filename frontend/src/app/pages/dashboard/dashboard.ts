import { ChangeDetectorRef, Component, computed, inject, OnDestroy, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { catchError, forkJoin, map, of, Subscription, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { DashboardPrenotazione, PlanimetriaResponse } from '../../core/app.models';
import { apiErrorMessage } from '../../core/api-error.utils';
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
import { BOOKING_ROLES, LOCATION_MANAGEMENT_ROLES, PLAN_EDITOR_ROLES } from '../../core/role-access';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, FormsModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class DashboardComponent implements OnInit, OnDestroy {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly user = this.auth.currentUser;
  bookings: DashboardPrenotazione[] = [];
  bookingsLoading = false;
  bookingsError = '';
  pendingGuestsCount = 0;
  planPreviewByPianoId = new Map<number, string>();
  private planPreviewSubscription: Subscription | null = null;
  deletingBookingId: number | null = null;
  bookingPendingDeletion: DashboardPrenotazione | null = null;
  editingBookingId: number | null = null;
  savingBookingId: number | null = null;
  editBookingDate = '';
  editStartTime = BOOKING_DAY_START;
  editEndTime = BOOKING_DAY_END;
  editError = '';
  editMessage = '';
  readonly minBookingDate = nextBookableIsoDate();
  readonly bookingStartOptions = BOOKING_START_OPTIONS;
  readonly bookingEndOptions = BOOKING_END_OPTIONS;

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
  protected readonly canAccessLocations = computed(() => this.auth.hasAnyRole(LOCATION_MANAGEMENT_ROLES));
  protected readonly isReception = computed(() => this.auth.hasAnyRole(['RECEPTION']));
  protected readonly canManageUsers = computed(() => this.auth.hasAnyRole(['ADMIN', 'RECEPTION']));
  protected readonly canBook = computed(() =>
    this.auth.hasAnyRole(BOOKING_ROLES),
  );

  ngOnInit(): void {
    this.loadBookings();
    this.loadPendingGuests();
  }

  ngOnDestroy(): void {
    this.planPreviewSubscription?.unsubscribe();
    this.clearPlanPreviews();
  }

  formatDate(value: string): string {
    const date = new Date(`${value}T00:00:00`);
    return date.toLocaleDateString('it-IT', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  }

  askCancelBooking(booking: DashboardPrenotazione): void {
    if (this.deletingBookingId || this.savingBookingId) {
      return;
    }

    this.bookingPendingDeletion = booking;
    this.cdr.detectChanges();
  }

  closeCancelBookingModal(): void {
    if (this.deletingBookingId) {
      return;
    }

    this.bookingPendingDeletion = null;
    this.cdr.detectChanges();
  }

  cancelBooking(): void {
    const booking = this.bookingPendingDeletion;
    if (!booking || this.deletingBookingId) {
      return;
    }

    this.deletingBookingId = booking.id;
    this.bookingsError = '';
    this.api.cancelBooking(booking.id).subscribe({
      next: () => {
        this.bookings = this.bookings.filter((item) => item.id !== booking.id);
        this.deletingBookingId = null;
        this.bookingPendingDeletion = null;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.deletingBookingId = null;
        this.bookingPendingDeletion = null;
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

  bookingResourceLabel(booking: DashboardPrenotazione): string {
    return booking.risorsaLabel || booking.meetingRoomNome || booking.postazioneCodice || 'Risorsa non disponibile';
  }

  bookingResourceTypeLabel(booking: DashboardPrenotazione): string {
    return booking.tipoRisorsaPrenotata === 'MEETING_ROOM' ? 'Sala riunioni' : 'Postazione';
  }

  planPreviewUrl(booking: DashboardPrenotazione): string {
    return booking.pianoId ? this.planPreviewByPianoId.get(booking.pianoId) ?? '' : '';
  }

  availableEditStartTimes(): readonly string[] {
    return this.bookingStartOptions.filter((time) => time < this.editEndTime);
  }

  availableEditEndTimes(): readonly string[] {
    return this.bookingEndOptions.filter((time) => time > this.editStartTime);
  }

  onEditTimeChange(): void {
    this.normalizeEditTimeWindow();
    this.editError = '';
    this.editMessage = '';
    this.cdr.detectChanges();
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
        this.loadPlanPreviews(this.bookings);
        this.bookingsLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.bookings = [];
        this.clearPlanPreviews();
        this.bookingsLoading = false;
        this.bookingsError = apiErrorMessage(err, 'Impossibile caricare le prenotazioni.');
        this.cdr.detectChanges();
      },
    });
  }

  private loadPlanPreviews(bookings: DashboardPrenotazione[]): void {
    this.planPreviewSubscription?.unsubscribe();
    this.clearPlanPreviews();

    const pianoIds = Array.from(new Set(
      bookings
        .map((booking) => booking.pianoId)
        .filter((pianoId): pianoId is number => typeof pianoId === 'number'),
    ));

    if (!pianoIds.length) {
      return;
    }

    this.planPreviewSubscription = forkJoin(
      pianoIds.map((pianoId) =>
        this.api.getPlanimetria(pianoId).pipe(
          switchMap((planimetria) => {
            if (!planimetria || !this.isPreviewablePlan(planimetria)) {
              return of({ pianoId, url: '' });
            }
            return this.api.getPlanimetriaImage(pianoId).pipe(
              map((blob) => ({ pianoId, url: URL.createObjectURL(blob) })),
            );
          }),
          catchError(() => of({ pianoId, url: '' })),
        ),
      ),
    ).subscribe({
      next: (previews) => {
        this.planPreviewByPianoId = new Map(
          previews.filter((preview) => preview.url).map((preview) => [preview.pianoId, preview.url]),
        );
        this.cdr.detectChanges();
      },
    });
  }

  private clearPlanPreviews(): void {
    this.planPreviewByPianoId.forEach((url) => URL.revokeObjectURL(url));
    this.planPreviewByPianoId = new Map();
  }

  private isPreviewablePlan(planimetria: PlanimetriaResponse): boolean {
    const imageName = planimetria.imageName?.toLowerCase() ?? '';
    return (
      planimetria.formatoOriginale !== 'DXF'
      && planimetria.formatoOriginale !== 'DWG'
    ) || /\.(svg|png|jpg|jpeg)$/.test(imageName);
  }

  private loadPendingGuests(): void {
    if (!this.canManageUsers()) {
      return;
    }

    this.api.listUsers().subscribe({
      next: (users) => {
        this.pendingGuestsCount = users.filter((user) => user.ruolo === 'GUEST').length;
        this.cdr.detectChanges();
      },
      error: () => {
        this.pendingGuestsCount = 0;
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
