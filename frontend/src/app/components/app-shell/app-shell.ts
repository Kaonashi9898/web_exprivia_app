import { ChangeDetectorRef, Component, OnDestroy, OnInit, computed, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { finalize } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { apiErrorMessage } from '../../core/api-error.utils';
import { AuthService } from '../../core/auth.service';
import { Gruppo, RuoloUtente } from '../../core/app.models';
import { PasswordResetNotificationsService } from '../../core/password-reset-notifications.service';
import {
  BOOKING_ROLES,
  LOCATION_MANAGEMENT_ROLES,
  PLAN_EDITOR_ROLES,
  USER_MANAGEMENT_ROLES,
} from '../../core/role-access';

const PASSWORD_RESET_REFRESH_INTERVAL_MS = 3000;

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css',
})
export class AppShellComponent implements OnInit, OnDestroy {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly passwordResetNotifications = inject(PasswordResetNotificationsService);
  protected readonly user = this.auth.currentUser;
  protected readonly passwordResetCount = this.passwordResetNotifications.count;
  protected readonly showBookings = computed(() =>
    this.auth.hasAnyRole(BOOKING_ROLES),
  );
  protected readonly showUsers = computed(() => this.auth.hasAnyRole(USER_MANAGEMENT_ROLES));
  protected readonly showLocations = computed(() => this.auth.hasAnyRole(LOCATION_MANAGEMENT_ROLES));
  protected readonly showPlan = computed(() => this.auth.hasAnyRole(PLAN_EDITOR_ROLES));
  showProfileModal = false;
  profileFullName = '';
  profileCurrentPassword = '';
  profileNewPassword = '';
  profileConfirmPassword = '';
  profileGroups: Gruppo[] = [];
  profileGroupsLoading = false;
  profileGroupsError = '';
  profileInfoSaving = false;
  profilePasswordSaving = false;
  profileMessage = '';
  profileError = '';
  private passwordResetRefreshTimer: number | null = null;
  private previousBodyOverflow = '';
  private readonly handleWindowFocus = () => this.refreshPasswordResetNotifications();
  private readonly handleVisibilityChange = () => {
    if (!document.hidden) {
      this.refreshPasswordResetNotifications();
    }
  };
  private readonly passwordResetAccessEffect = effect(() => {
    if (this.showUsers()) {
      this.startPasswordResetRefresh();
      this.passwordResetNotifications.refresh();
      return;
    }

    this.stopPasswordResetRefresh();
    this.passwordResetNotifications.reset();
  });

  ngOnInit(): void {
    window.addEventListener('focus', this.handleWindowFocus);
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
  }

  ngOnDestroy(): void {
    this.stopPasswordResetRefresh();
    this.unlockBodyScroll();
    window.removeEventListener('focus', this.handleWindowFocus);
    document.removeEventListener('visibilitychange', this.handleVisibilityChange);
  }

  logout(): void {
    this.passwordResetNotifications.reset();
    this.auth.logout();
  }

  openProfileModal(): void {
    const currentUser = this.user();
    if (!currentUser) {
      return;
    }

    this.showProfileModal = true;
    this.lockBodyScroll();
    this.profileFullName = currentUser.fullName;
    this.profileCurrentPassword = '';
    this.profileNewPassword = '';
    this.profileConfirmPassword = '';
    this.profileGroups = [];
    this.profileGroupsLoading = true;
    this.profileGroupsError = '';
    this.profileMessage = '';
    this.profileError = '';
    this.refreshView();

    this.api.listMyGroups().pipe(
      finalize(() => {
        this.profileGroupsLoading = false;
        this.refreshView();
      }),
    ).subscribe({
      next: (groups) => {
        this.profileGroups = groups;
        this.profileGroupsError = '';
        this.refreshView();
      },
      error: (err) => {
        this.profileGroups = [];
        this.profileGroupsError = apiErrorMessage(err, 'Impossibile caricare i gruppi del profilo.');
        this.refreshView();
      },
    });
  }

  closeProfileModal(): void {
    if (this.profileInfoSaving || this.profilePasswordSaving) {
      return;
    }

    this.showProfileModal = false;
    this.unlockBodyScroll();
    this.profileCurrentPassword = '';
    this.profileNewPassword = '';
    this.profileConfirmPassword = '';
    this.profileMessage = '';
    this.profileError = '';
    this.refreshView();
  }

  saveProfileInfo(): void {
    const currentUser = this.user();
    if (!currentUser || this.profileInfoSaving) {
      return;
    }

    const fullName = this.profileFullName.trim().replace(/\s+/g, ' ');
    if (!fullName) {
      this.profileError = 'Il nome completo e\' obbligatorio.';
      this.profileMessage = '';
      this.refreshView();
      return;
    }
    if (fullName.length > 50) {
      this.profileError = 'Il nome non puo\' superare i 50 caratteri.';
      this.profileMessage = '';
      this.refreshView();
      return;
    }
    if (fullName === currentUser.fullName) {
      this.profileMessage = 'Nessuna modifica da salvare nel profilo.';
      this.profileError = '';
      this.refreshView();
      return;
    }

    this.profileInfoSaving = true;
    this.profileError = '';
    this.profileMessage = '';
    this.auth.updateMyProfile({ fullName }).pipe(
      finalize(() => {
        this.profileInfoSaving = false;
        this.refreshView();
      }),
    ).subscribe({
      next: (updatedUser) => {
        this.profileFullName = updatedUser.fullName;
        this.profileMessage = 'Profilo aggiornato correttamente.';
        this.refreshView();
      },
      error: (err) => {
        this.profileError = apiErrorMessage(err, 'Aggiornamento profilo non riuscito.');
        this.refreshView();
      },
    });
  }

  saveProfilePassword(): void {
    if (this.profilePasswordSaving) {
      return;
    }

    if (!this.profileCurrentPassword || !this.profileNewPassword || !this.profileConfirmPassword) {
      this.profileError = 'Compila tutti i campi della password.';
      this.profileMessage = '';
      this.refreshView();
      return;
    }
    if (this.profileNewPassword.length < 8) {
      this.profileError = 'La nuova password deve essere di almeno 8 caratteri.';
      this.profileMessage = '';
      this.refreshView();
      return;
    }
    if (this.profileNewPassword !== this.profileConfirmPassword) {
      this.profileError = 'La conferma password non coincide.';
      this.profileMessage = '';
      this.refreshView();
      return;
    }

    this.profilePasswordSaving = true;
    this.profileError = '';
    this.profileMessage = '';
    this.auth.changeMyPassword({
      currentPassword: this.profileCurrentPassword,
      newPassword: this.profileNewPassword,
    }).pipe(
      finalize(() => {
        this.profilePasswordSaving = false;
        this.refreshView();
      }),
    ).subscribe({
      next: () => {
        this.profileCurrentPassword = '';
        this.profileNewPassword = '';
        this.profileConfirmPassword = '';
        this.profileMessage = 'Password aggiornata correttamente.';
        this.refreshView();
      },
      error: (err) => {
        this.profileError = apiErrorMessage(err, 'Aggiornamento password non riuscito.');
        this.refreshView();
      },
    });
  }

  protected passwordResetBadgeLabel(): string {
    const count = this.passwordResetCount();
    return count > 99 ? '99+' : String(count);
  }

  protected profileRoleLabel(): string {
    return this.roleLabel(this.user()?.ruolo ?? null);
  }

  private roleLabel(role: RuoloUtente | null | undefined): string {
    const labels: Record<RuoloUtente, string> = {
      ADMIN: 'Amministratore',
      BUILDING_MANAGER: 'Building manager',
      RECEPTION: 'Reception',
      USER: 'Dipendente',
      GUEST: 'Guest',
    };
    return role ? labels[role] : '';
  }

  private refreshPasswordResetNotifications(): void {
    if (this.showUsers()) {
      this.passwordResetNotifications.refresh();
    } else {
      this.passwordResetNotifications.reset();
    }
  }

  private startPasswordResetRefresh(): void {
    if (this.passwordResetRefreshTimer !== null) {
      return;
    }

    this.passwordResetRefreshTimer = window.setInterval(() => {
      this.refreshPasswordResetNotifications();
    }, PASSWORD_RESET_REFRESH_INTERVAL_MS);
  }

  private stopPasswordResetRefresh(): void {
    if (this.passwordResetRefreshTimer === null) {
      return;
    }

    window.clearInterval(this.passwordResetRefreshTimer);
    this.passwordResetRefreshTimer = null;
  }

  private refreshView(): void {
    this.cdr.detectChanges();
  }

  private lockBodyScroll(): void {
    this.previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
  }

  private unlockBodyScroll(): void {
    document.body.style.overflow = this.previousBodyOverflow;
  }
}
