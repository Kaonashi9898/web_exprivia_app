import { ChangeDetectorRef, Component, computed, inject, OnDestroy, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { catchError, forkJoin, map, of, Subscription, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { DashboardPrenotazione, PlanimetriaResponse, PrenotazioneNotifica, Sede, WeatherSnapshot } from '../../core/app.models';
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
  futureBookings: DashboardPrenotazione[] = [];
  pastBookings: DashboardPrenotazione[] = [];
  bookingsLoading = false;
  bookingsError = '';
  selectedBookingSection: 'future' | 'past' = 'future';
  pendingApprovalMessage = '';
  pendingGuestsCount = 0;
  planPreviewByPianoId = new Map<number, string>();
  private planPreviewSubscription: Subscription | null = null;
  deletingBookingId: number | null = null;
  bookingPendingDeletion: DashboardPrenotazione | null = null;
  editingBookingId: number | null = null;
  savingBookingId: number | null = null;
  cancellationNotifications: PrenotazioneNotifica[] = [];
  notificationAcknowledgeBusy = false;
  notificationError = '';
  weather: WeatherSnapshot | null = null;
  weatherLoading = false;
  weatherError = '';
  weatherLocationLabel = '';
  weatherSourceLabel = '';
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
  protected readonly isGuest = computed(() => this.auth.hasAnyRole(['GUEST']));
  protected readonly canAccessPlanEditor = computed(() => this.auth.hasAnyRole(PLAN_EDITOR_ROLES));
  protected readonly canAccessLocations = computed(() => this.auth.hasAnyRole(LOCATION_MANAGEMENT_ROLES));
  protected readonly isReception = computed(() => this.auth.hasAnyRole(['RECEPTION']));
  protected readonly canManageUsers = computed(() => this.auth.hasAnyRole(['ADMIN', 'RECEPTION']));
  protected readonly canBook = computed(() =>
    this.auth.hasAnyRole(BOOKING_ROLES),
  );
  protected readonly canSeeWeather = computed(() => this.auth.hasAnyRole(['USER']));

  ngOnInit(): void {
    this.loadBookings();
    this.loadPendingGuests();
    this.loadCancellationNotifications();
    this.loadWeatherFallback();
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
        this.refreshBookingSections();
        this.deletingBookingId = null;
        this.bookingPendingDeletion = null;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.deletingBookingId = null;
        this.bookingPendingDeletion = null;
        this.bookingsError = bookingCancellationErrorMessage(err);
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
    return !this.isBookingInProgress(booking) && !this.isPastBooking(booking) && booking.dataPrenotazione >= this.minBookingDate;
  }

  canCancelBooking(booking: DashboardPrenotazione): boolean {
    return !this.isBookingInProgress(booking) && !this.isPastBooking(booking);
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

  visibleBookings(): DashboardPrenotazione[] {
    return this.showingFutureBookings() ? this.futureBookings : this.pastBookings;
  }

  showingFutureBookings(): boolean {
    return this.selectedBookingSection === 'future';
  }

  showingPastBookings(): boolean {
    return this.selectedBookingSection === 'past';
  }

  selectFutureBookings(): void {
    this.selectedBookingSection = 'future';
    this.cdr.detectChanges();
  }

  selectPastBookings(): void {
    this.selectedBookingSection = 'past';
    this.cdr.detectChanges();
  }

  visibleBookingsTitle(): string {
    return this.showingFutureBookings() ? 'Prenotazioni future' : 'Prenotazioni passate';
  }

  visibleBookingsCount(): number {
    return this.showingFutureBookings() ? this.futureBookings.length : this.pastBookings.length;
  }

  useCurrentPositionForWeather(): void {
    if (!this.canSeeWeather()) {
      return;
    }
    if (!navigator.geolocation) {
      this.weatherError = 'Geolocalizzazione non supportata dal browser.';
      this.cdr.detectChanges();
      return;
    }

    this.weatherLoading = true;
    this.weatherError = '';
    this.cdr.detectChanges();

    navigator.geolocation.getCurrentPosition(
      (position) => {
        this.loadWeather(position.coords.latitude, position.coords.longitude, 'Posizione corrente', 'Meteo locale');
      },
      () => {
        this.weatherLoading = false;
        this.weatherError = 'Posizione non disponibile. Ti mostro il meteo della sede aziendale, se presente.';
        this.cdr.detectChanges();
        if (!this.weather) {
          this.loadWeatherFallback();
        }
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 15 * 60 * 1000 },
    );
  }

  weatherSummary(): string {
    if (!this.weather) {
      return 'Meteo non disponibile';
    }
    return this.describeWeatherCode(this.weather.weatherCode, this.weather.isDay);
  }

  weatherTemperature(): string {
    if (!this.weather) {
      return '--';
    }
    return `${Math.round(this.weather.temperatureC)}°C`;
  }

  weatherRange(): string {
    if (!this.weather) {
      return '--';
    }
    return `Min ${Math.round(this.weather.minTemperatureC)}° / Max ${Math.round(this.weather.maxTemperatureC)}°`;
  }

  weatherIconType(): 'sun' | 'cloud' | 'rain' | 'storm' | 'snow' {
    if (!this.weather) {
      return 'sun';
    }

    const code = this.weather.weatherCode;
    if ([95, 96, 99].includes(code)) {
      return 'storm';
    }
    if ([71, 73, 75, 77, 85, 86].includes(code)) {
      return 'snow';
    }
    if ([45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82].includes(code)) {
      return 'rain';
    }
    if ([2, 3].includes(code)) {
      return 'cloud';
    }
    return 'sun';
  }

  isPastBooking(booking: DashboardPrenotazione): boolean {
    return this.bookingEndsAt(booking).getTime() <= Date.now();
  }

  isBookingInProgress(booking: DashboardPrenotazione): boolean {
    const now = Date.now();
    return this.bookingStartsAt(booking).getTime() <= now && this.bookingEndsAt(booking).getTime() > now;
  }

  bookingReadOnlyLabel(booking: DashboardPrenotazione): string {
    return this.isBookingInProgress(booking) ? 'Azioni disabilitate' : 'Solo consultazione';
  }

  hasCancellationNotifications(): boolean {
    return this.cancellationNotifications.length > 0;
  }

  acknowledgeCancellationNotifications(): void {
    if (!this.cancellationNotifications.length || this.notificationAcknowledgeBusy) {
      return;
    }

    this.notificationAcknowledgeBusy = true;
    this.notificationError = '';
    this.api.acknowledgeMyBookingCancellationNotifications().subscribe({
      next: () => {
        this.cancellationNotifications = [];
        this.notificationAcknowledgeBusy = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.notificationAcknowledgeBusy = false;
        this.notificationError = apiErrorMessage(err, 'Impossibile confermare la lettura delle notifiche.');
        this.cdr.detectChanges();
      },
    });
  }

  notificationStatusLabel(notification: PrenotazioneNotifica): string {
    return notification.statoPostazione.replaceAll('_', ' ');
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
          .map((item) => item.id === booking.id ? { ...item, ...updated } : item);
        this.refreshBookingSections();
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
    if (this.isGuest()) {
      this.bookings = [];
      this.refreshBookingSections();
      this.bookingsLoading = false;
      this.bookingsError = '';
      this.pendingApprovalMessage = "Registrazione avvenuta. Attendi che l'admin approvi il tuo account per accedere alle prenotazioni.";
      this.clearPlanPreviews();
      this.cdr.detectChanges();
      return;
    }

    this.bookingsLoading = true;
    this.bookingsError = '';
    this.pendingApprovalMessage = '';

    this.api.listMyDashboardBookings().subscribe({
      next: (bookings) => {
        this.bookings = bookings
          .filter((booking) => booking.stato === 'CONFERMATA')
          .map((booking) => ({
            ...booking,
            sedeLabel: booking.sedeLabel || 'Sede non disponibile',
            pianoLabel: booking.pianoLabel || 'Piano non disponibile',
          }));
        this.refreshBookingSections();
        this.loadPlanPreviews(this.bookings);
        this.bookingsLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.bookings = [];
        this.refreshBookingSections();
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

  private loadCancellationNotifications(): void {
    if (this.isGuest()) {
      this.cancellationNotifications = [];
      this.notificationError = '';
      this.cdr.detectChanges();
      return;
    }

    this.api.listMyBookingCancellationNotifications().subscribe({
      next: (notifications) => {
        this.cancellationNotifications = notifications;
        this.notificationError = '';
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.cancellationNotifications = [];
        this.notificationError = apiErrorMessage(err, 'Impossibile caricare le notifiche di annullamento.');
        this.cdr.detectChanges();
      },
    });
  }

  private loadWeatherFallback(): void {
    if (!this.canSeeWeather()) {
      this.weather = null;
      this.weatherLoading = false;
      this.weatherError = '';
      this.cdr.detectChanges();
      return;
    }

    this.weatherLoading = true;
    this.weatherError = '';
    this.api.listSedi().pipe(
      map((sedi) => this.selectFallbackSede(sedi)),
      switchMap((sede) => {
        if (!sede || sede.latitudine == null || sede.longitudine == null) {
          return of(null);
        }
        return this.api.getWeatherForecast(sede.latitudine, sede.longitudine).pipe(
          map((weather) => ({
            weather,
            locationLabel: this.buildSedeWeatherLabel(sede),
            sourceLabel: 'Sede aziendale',
          })),
          catchError(() => of(null)),
        );
      }),
    ).subscribe({
      next: (result) => {
        this.weatherLoading = false;
        if (!result) {
          this.weather = null;
          this.weatherLocationLabel = '';
          this.weatherSourceLabel = '';
          if (!this.weatherError) {
            this.weatherError = 'Nessuna sede con coordinate disponibili per il meteo.';
          }
          this.cdr.detectChanges();
          return;
        }
        this.weather = result.weather;
        this.weatherLocationLabel = result.locationLabel;
        this.weatherSourceLabel = result.sourceLabel;
        this.cdr.detectChanges();
      },
      error: () => {
        this.weatherLoading = false;
        this.weather = null;
        if (!this.weatherError) {
          this.weatherError = 'Impossibile caricare il meteo della sede aziendale.';
        }
        this.cdr.detectChanges();
      },
    });
  }

  private loadWeather(latitude: number, longitude: number, locationLabel: string, sourceLabel: string): void {
    this.api.getWeatherForecast(latitude, longitude).subscribe({
      next: (weather) => {
        this.weather = weather;
        this.weatherLoading = false;
        this.weatherError = '';
        this.weatherLocationLabel = locationLabel;
        this.weatherSourceLabel = sourceLabel;
        this.cdr.detectChanges();
      },
      error: () => {
        this.weatherLoading = false;
        this.weatherError = 'Impossibile aggiornare il meteo in questo momento.';
        this.cdr.detectChanges();
      },
    });
  }

  private selectFallbackSede(sedi: Sede[]): Sede | null {
    return sedi.find((sede) => sede.latitudine != null && sede.longitudine != null) ?? null;
  }

  private buildSedeWeatherLabel(sede: Sede): string {
    const nome = sede.nome?.trim();
    const citta = sede.citta?.trim();
    if (nome && citta) {
      return `${nome} - ${citta}`;
    }
    return nome || citta || 'Sede aziendale';
  }

  private describeWeatherCode(code: number, isDay: boolean): string {
    const clearLabel = isDay ? 'Sereno' : 'Cielo sereno';
    if (code === 0) {
      return clearLabel;
    }
    if (code === 1) {
      return isDay ? 'Prevalentemente sereno' : 'Prevalentemente limpido';
    }
    if (code === 2) {
      return 'Parzialmente nuvoloso';
    }
    if (code === 3) {
      return 'Coperto';
    }
    if (code === 45 || code === 48) {
      return 'Nebbia';
    }
    if ([51, 53, 55, 56, 57].includes(code)) {
      return 'Pioviggine';
    }
    if ([61, 63, 65, 66, 67, 80, 81, 82].includes(code)) {
      return 'Pioggia';
    }
    if ([71, 73, 75, 77, 85, 86].includes(code)) {
      return 'Neve';
    }
    if ([95, 96, 99].includes(code)) {
      return 'Temporale';
    }
    return 'Condizioni variabili';
  }

  private refreshBookingSections(): void {
    this.futureBookings = this.bookings
      .filter((booking) => !this.isPastBooking(booking))
      .sort((left, right) =>
        `${left.dataPrenotazione} ${left.oraInizio}`.localeCompare(`${right.dataPrenotazione} ${right.oraInizio}`),
      );
    this.pastBookings = this.bookings
      .filter((booking) => this.isPastBooking(booking))
      .sort((left, right) =>
        `${right.dataPrenotazione} ${right.oraInizio}`.localeCompare(`${left.dataPrenotazione} ${left.oraInizio}`),
      );
    this.selectedBookingSection = this.futureBookings.length || !this.pastBookings.length ? 'future' : 'past';
  }

  private bookingStartsAt(booking: DashboardPrenotazione): Date {
    return new Date(`${booking.dataPrenotazione}T${booking.oraInizio}:00`);
  }

  private bookingEndsAt(booking: DashboardPrenotazione): Date {
    return new Date(`${booking.dataPrenotazione}T${booking.oraFine}:00`);
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
