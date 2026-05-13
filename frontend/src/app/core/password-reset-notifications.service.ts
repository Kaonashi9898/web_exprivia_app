import { Injectable, inject, signal } from '@angular/core';
import { catchError, finalize, forkJoin, map, of } from 'rxjs';
import { ApiService } from './api.service';
import { PasswordResetRequest, Utente } from './app.models';

@Injectable({ providedIn: 'root' })
export class PasswordResetNotificationsService {
  private readonly api = inject(ApiService);
  private readonly countSignal = signal(0);
  private loading = false;

  readonly count = this.countSignal.asReadonly();

  refresh(): void {
    if (this.loading) {
      return;
    }

    this.loading = true;
    forkJoin({
      passwordResetRequests: this.api.listPasswordResetRequests().pipe(catchError(() => of([] as PasswordResetRequest[]))),
      users: this.api.listUsers().pipe(catchError(() => of([] as Utente[]))),
    }).pipe(
      map(({ passwordResetRequests, users }) => this.countNotifications(passwordResetRequests, users)),
      catchError(() => of(0)),
      finalize(() => {
        this.loading = false;
      }),
    ).subscribe((count) => this.setCount(count));
  }

  setCount(count: number): void {
    this.countSignal.set(Math.max(0, count));
  }

  setFromData(passwordResetRequests: PasswordResetRequest[], users: Utente[]): void {
    this.setCount(this.countNotifications(passwordResetRequests, users));
  }

  reset(): void {
    this.countSignal.set(0);
  }

  private countNotifications(passwordResetRequests: PasswordResetRequest[], users: Utente[]): number {
    return passwordResetRequests.length + users.filter((user) => user.ruolo === 'GUEST').length;
  }
}
