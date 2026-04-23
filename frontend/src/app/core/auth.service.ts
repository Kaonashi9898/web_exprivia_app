import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, Observable, of, switchMap, tap } from 'rxjs';
import { LoginResponse, RuoloUtente, Utente } from './app.models';
import { environment } from '../../environments/environment';

const TOKEN_KEY = 'exprivia-booking-token';
const UTENTI_API = environment.utentiApiBaseUrl;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly token = signal<string | null>(this.readStoredToken());
  readonly currentUser = signal<Utente | null>(this.userFromToken(this.token()));
  readonly isAuthenticated = computed(() => this.isTokenValid(this.token()));
  readonly ruolo = computed(() => this.currentUser()?.ruolo ?? this.roleFromToken(this.token()));

  login(email: string, password: string): Observable<Utente> {
    return this.http.post<LoginResponse>(`${UTENTI_API}/api/auth/login`, { email, password }).pipe(
      tap((response) => this.setToken(response.token)),
      switchMap(() => this.loadProfile() as Observable<Utente>),
    );
  }

  loadProfile(): Observable<Utente | null> {
    if (!this.getValidToken()) {
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

  getValidToken(): string | null {
    const token = this.token();
    if (!this.isTokenValid(token)) {
      this.logout(false);
      return null;
    }
    return token;
  }

  private roleFromToken(token: string | null): RuoloUtente | null {
    if (!this.isTokenValid(token)) {
      return null;
    }
    return this.decodeToken(token)?.['ruolo'] as RuoloUtente | null;
  }

  private userFromToken(token: string | null): Utente | null {
    if (!this.isTokenValid(token)) {
      return null;
    }

    const payload = this.decodeToken(token);
    if (!payload) {
      return null;
    }

    const subject = payload['sub'] as string | undefined;
    const fullName = payload['fullName'] as string | undefined;

    return {
      id: 0,
      fullName: fullName ?? subject ?? 'Utente Exprivia',
      email: subject ?? '',
      ruolo: payload['ruolo'] as RuoloUtente,
    };
  }

  private readStoredToken(): string | null {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!this.isTokenValid(token)) {
      localStorage.removeItem(TOKEN_KEY);
      return null;
    }
    return token;
  }

  private isTokenValid(token: string | null): boolean {
    const payload = this.decodeToken(token);
    if (!payload) {
      return false;
    }

    const expValue = payload['exp'];
    if (expValue == null) {
      return true;
    }

    const expSeconds = typeof expValue === 'number' ? expValue : Number(expValue);
    if (!Number.isFinite(expSeconds)) {
      return false;
    }

    return expSeconds * 1000 > Date.now();
  }

  private decodeToken(token: string | null): Record<string, unknown> | null {
    if (!token) {
      return null;
    }

    try {
      const payload = token.split('.')[1];
      if (!payload) {
        return null;
      }
      const normalizedPayload = payload.replace(/-/g, '+').replace(/_/g, '/');
      const paddedPayload = normalizedPayload.padEnd(Math.ceil(normalizedPayload.length / 4) * 4, '=');
      const decoded = atob(paddedPayload);
      return JSON.parse(decoded);
    } catch {
      return null;
    }
  }
}
