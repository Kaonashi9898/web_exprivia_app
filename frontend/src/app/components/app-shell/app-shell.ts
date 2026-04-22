import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';

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
    this.auth.hasAnyRole(['ADMIN', 'RECEPTION', 'USER']),
  );
  protected readonly showUsers = computed(() => this.auth.hasAnyRole(['ADMIN']));
  protected readonly showLocations = computed(() =>
    this.auth.hasAnyRole(['ADMIN', 'RECEPTION', 'USER']),
  );
  protected readonly showPlan = computed(() => this.auth.hasAnyRole(['ADMIN', 'BUILDING_MANAGER']));

  logout(): void {
    this.auth.logout();
  }
}
