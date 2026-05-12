import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, map, Observable, of, tap } from 'rxjs';
import { LoginResponse, RegisterRequest, RuoloUtente, Utente } from './app.models';
import { environment } from '../../environments/environment';

const LEGACY_TOKEN_KEY = 'exprivia-booking-token';
const UTENTI_API = environment.utentiApiBaseUrl;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly currentUser = signal<Utente | null>(null);
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly ruolo = computed(() => this.currentUser()?.ruolo ?? null);

  constructor() {
    this.clearLegacyStoredToken();
  }

  login(email: string, password: string): Observable<Utente> {
    return this.http.post<LoginResponse>(`${UTENTI_API}/api/auth/login`, { email, password }).pipe(
      tap((response) => this.currentUser.set(response.user)),
      map((response) => response.user),
    );
  }

  register(request: RegisterRequest): Observable<Utente> {
    return this.http.post<Utente>(`${UTENTI_API}/api/auth/register`, request);
  }

  requestPasswordReset(email: string): Observable<void> {
    return this.http.post<void>(`${UTENTI_API}/api/auth/password-reset-request`, { email });
  }

  loadProfile(): Observable<Utente | null> {
    return this.http.get<Utente>(`${UTENTI_API}/api/utenti/me`).pipe(
      tap((user) => this.currentUser.set(user)),
      catchError(() => {
        this.clearSession();
        return of(null);
      }),
    );
  }

  ensureProfile(): Observable<Utente | null> {
    const user = this.currentUser();
    return user ? of(user) : this.loadProfile();
  }

  hasAnyRole(roles: RuoloUtente[]): boolean {
    const ruolo = this.ruolo();
    return !!ruolo && roles.includes(ruolo);
  }

  logout(navigate = true): void {
    this.clearSession();
    this.http.post<void>(`${UTENTI_API}/api/auth/logout`, {}).pipe(
      catchError(() => of(undefined)),
    ).subscribe();
    if (navigate) {
      this.router.navigateByUrl('/');
    }
  }

  private clearSession(): void {
    this.currentUser.set(null);
  }

  private clearLegacyStoredToken(): void {
    window.localStorage?.removeItem(LEGACY_TOKEN_KEY);
  }
}
