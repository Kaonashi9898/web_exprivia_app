import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, retry, timer, timeout } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { RegisterRequest, RuoloUtente, Utente } from '../../core/app.models';
import { apiErrorMessage } from '../../core/api-error.utils';

@Component({
  selector: 'app-users',
  imports: [FormsModule],
  templateUrl: './users.html',
  styleUrl: './users.css',
})
export class UsersComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private createRefreshTimers: number[] = [];
  private initialRefreshTimers: number[] = [];

  roles: RuoloUtente[] = ['USER', 'BUILDING_MANAGER', 'RECEPTION', 'ADMIN', 'GUEST'];
  users: Utente[] = [];
  loading = false;
  saving = false;
  message = '';
  error = '';
  form: RegisterRequest = this.emptyForm();
  showPassword = false;

  ngOnInit(): void {
    this.loadUsers();
    this.initialRefreshTimers = [600, 1600].map((delay) =>
      window.setTimeout(() => this.loadUsers(false), delay),
    );
  }

  ngOnDestroy(): void {
    this.clearCreateRefreshTimers();
    this.initialRefreshTimers.forEach((timerId) => window.clearTimeout(timerId));
  }

  loadUsers(clearError = true): void {
    this.loading = true;
    if (clearError) {
      this.error = '';
    }
    this.api
      .listUsers()
      .pipe(
        retry({ count: 2, delay: () => timer(350) }),
        timeout(7000),
        finalize(() => {
          this.loading = false;
          this.refreshView();
        }),
      )
      .subscribe({
        next: (users) => {
          this.users = users;
          this.refreshView();
        },
        error: (err) => {
          this.error = apiErrorMessage(err, 'Impossibile caricare gli utenti.');
          this.refreshView();
        },
      });
  }

  createUser(): void {
    if (this.saving) {
      return;
    }

    this.saving = true;
    this.error = '';
    this.message = '';

    this.scheduleCreateRefresh();
    this.api
      .createUser(this.form)
      .pipe(
        timeout(7000),
        finalize(() => {
          this.saving = false;
          this.refreshView();
        }),
      )
      .subscribe({
        next: () => {
          this.clearCreateRefreshTimers();
          this.form = this.emptyForm();
          this.message = 'Utente creato correttamente.';
          this.refreshView();
          this.loadUsers();
        },
        error: (err) => {
          this.clearCreateRefreshTimers();
          this.saving = false;
          this.error = apiErrorMessage(err, 'Creazione utente non riuscita.');
          this.refreshView();
          this.loadUsers(false);
        },
      });
  }

  updateRole(user: Utente, ruolo: RuoloUtente): void {
    this.api.updateUserRole(user.id, ruolo).subscribe({
      next: (updated) => {
        this.users = this.users.map((item) => (item.id === updated.id ? updated : item));
        this.message = 'Ruolo aggiornato.';
        this.refreshView();
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Aggiornamento ruolo non riuscito.');
        this.refreshView();
      },
    });
  }

  deleteUser(user: Utente): void {
    if (!confirm(`Eliminare ${user.fullName}?`)) {
      return;
    }

    this.api.deleteUser(user.id).subscribe({
      next: () => {
        this.users = this.users.filter((item) => item.id !== user.id);
        this.message = 'Utente eliminato.';
        this.refreshView();
        this.loadUsers(false);
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Eliminazione utente non riuscita.');
        this.refreshView();
      },
    });
  }

  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
    this.refreshView();
  }

  private emptyForm(): RegisterRequest {
    return {
      fullName: '',
      email: '',
      password: '',
      ruolo: 'USER',
    };
  }

  private scheduleCreateRefresh(): void {
    this.clearCreateRefreshTimers();
    this.createRefreshTimers = [1200, 3200].map((delay) =>
      window.setTimeout(() => {
        this.loadUsers(false);
        if (delay === 3200) {
          this.saving = false;
          this.refreshView();
        }
      }, delay),
    );
  }

  private clearCreateRefreshTimers(): void {
    this.createRefreshTimers.forEach((timerId) => window.clearTimeout(timerId));
    this.createRefreshTimers = [];
  }

  private refreshView(): void {
    this.cdr.detectChanges();
  }
}
