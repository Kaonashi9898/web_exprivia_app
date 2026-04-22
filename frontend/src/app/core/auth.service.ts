import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, Observable, of, switchMap, tap } from 'rxjs';
import { LoginResponse, RuoloUtente, Utente } from './app.models';

const TOKEN_KEY = 'exprivia-booking-token';
const UTENTI_API = 'http://localhost:8081';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly token = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  readonly currentUser = signal<Utente | null>(this.userFromToken(this.token()));
  readonly isAuthenticated = computed(() => !!this.token());
  readonly ruolo = computed(() => this.currentUser()?.ruolo ?? this.roleFromToken(this.token()));

  login(email: string, password: string): Observable<Utente> {
    return this.http.post<LoginResponse>(`${UTENTI_API}/api/auth/login`, { email, password }).pipe(
      tap((response) => this.setToken(response.token)),
      switchMap(() => this.loadProfile() as Observable<Utente>),
    );
  }

  loadProfile(): Observable<Utente | null> {
    if (!this.token()) {
      this.currentUser.set(null);
      return of(null);
    }

    return this.http.get<Utente>(`${UTENTI_API}/api/utenti/me`).pipe(
      tap((user) => this.currentUser.set(user)),
      catchError(() => {
        this.logout(false);
        return of(null);
      }),
    );
  }

  hasAnyRole(roles: RuoloUtente[]): boolean {
    const ruolo = this.ruolo();
    return !!ruolo && roles.includes(ruolo);
  }

  logout(navigate = true): void {
    localStorage.removeItem(TOKEN_KEY);
    this.token.set(null);
    this.currentUser.set(null);
    if (navigate) {
      this.router.navigateByUrl('/');
    }
  }

  private setToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
    this.token.set(token);
    this.currentUser.set(this.userFromToken(token));
  }

  private roleFromToken(token: string | null): RuoloUtente | null {
    return this.decodeToken(token)?.['ruolo'] as RuoloUtente | null;
  }

  private userFromToken(token: string | null): Utente | null {
    const payload = this.decodeToken(token);
    if (!payload) {
      return null;
    }

    return {
      id: 0,
      fullName: payload['sub'] ?? 'Utente Exprivia',
      email: payload['sub'] ?? '',
      ruolo: payload['ruolo'] as RuoloUtente,
    };
  }

  private decodeToken(token: string | null): Record<string, string> | null {
    if (!token) {
      return null;
    }

    try {
      const payload = token.split('.')[1];
      const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(decoded);
    } catch {
      return null;
    }
  }
}
