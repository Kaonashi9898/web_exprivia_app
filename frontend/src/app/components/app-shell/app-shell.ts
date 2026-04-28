import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import {
  BOOKING_ROLES,
  LOCATION_MANAGEMENT_ROLES,
  PLAN_EDITOR_ROLES,
  USER_MANAGEMENT_ROLES,
} from '../../core/role-access';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css',
})
export class AppShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly user = this.auth.currentUser;
  protected readonly showBookings = computed(() =>
    this.auth.hasAnyRole(BOOKING_ROLES),
  );
  protected readonly showUsers = computed(() => this.auth.hasAnyRole(USER_MANAGEMENT_ROLES));
  protected readonly showLocations = computed(() => this.auth.hasAnyRole(LOCATION_MANAGEMENT_ROLES));
  protected readonly showPlan = computed(() => this.auth.hasAnyRole(PLAN_EDITOR_ROLES));

  logout(): void {
    this.auth.logout();
  }
}
