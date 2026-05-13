import { Component, OnDestroy, OnInit, computed, effect, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
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
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css',
})
export class AppShellComponent implements OnInit, OnDestroy {
  protected readonly auth = inject(AuthService);
  private readonly passwordResetNotifications = inject(PasswordResetNotificationsService);
  protected readonly user = this.auth.currentUser;
  protected readonly passwordResetCount = this.passwordResetNotifications.count;
  protected readonly showBookings = computed(() =>
    this.auth.hasAnyRole(BOOKING_ROLES),
  );
  protected readonly showUsers = computed(() => this.auth.hasAnyRole(USER_MANAGEMENT_ROLES));
  protected readonly showLocations = computed(() => this.auth.hasAnyRole(LOCATION_MANAGEMENT_ROLES));
  protected readonly showPlan = computed(() => this.auth.hasAnyRole(PLAN_EDITOR_ROLES));
  private passwordResetRefreshTimer: number | null = null;
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
    window.removeEventListener('focus', this.handleWindowFocus);
    document.removeEventListener('visibilitychange', this.handleVisibilityChange);
  }

  logout(): void {
    this.passwordResetNotifications.reset();
    this.auth.logout();
  }

  protected passwordResetBadgeLabel(): string {
    const count = this.passwordResetCount();
    return count > 99 ? '99+' : String(count);
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
}
