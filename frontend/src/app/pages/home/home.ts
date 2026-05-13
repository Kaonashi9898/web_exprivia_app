import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize, timeout } from 'rxjs';
import { apiErrorMessage } from '../../core/api-error.utils';
import { AuthService } from '../../core/auth.service';

const DOMAIN_ERROR = 'Dominio non autorizzato. Utilizzare esclusivamente un indirizzo @exprivia.com';
const EXPRIVIA_EMAIL_PATTERN = /^[a-z0-9]+(?:[._-][a-z0-9]+)*@exprivia\.com$/i;
const REQUEST_TIMEOUT_MS = 5000;

@Component({
  selector: 'app-home',
  imports: [FormsModule],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class HomeComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly changeDetector = inject(ChangeDetectorRef);

  mode: 'login' | 'register' = 'login';
  email = '';
  password = '';
  registerFullName = '';
  registerEmail = '';
  registerPassword = '';
  loading = false;
  registering = false;
  error = '';
  message = '';
  registrationSuccessOpen = false;
  registrationSuccessEmail = '';
  showPassword = false;
  showRegisterPassword = false;
  forgotPasswordOpen = false;
  forgotPasswordEmail = '';
  forgotPasswordSubmitting = false;

  scrollTo(sectionId: string) {
    const section = document.getElementById(sectionId);
    if (section) {
      section.scrollIntoView({ behavior: 'smooth' });
    }
  }

  login() {
    this.error = '';
    this.message = '';
    const email = this.email.trim();
    if (!email || !this.password) {
      this.error = 'Inserisci email e password per accedere.';
      return;
    }
    if (!this.isExpriviaEmail(email)) {
      this.error = DOMAIN_ERROR;
      return;
    }

    this.loading = true;

    this.auth
      .login(email, this.password)
      .pipe(
        timeout(REQUEST_TIMEOUT_MS),
        finalize(() => {
          this.loading = false;
          this.renderNow();
        }),
      )
      .subscribe({
        next: () => this.router.navigateByUrl('/dashboard'),
        error: (err) => {
          if (err?.name === 'TimeoutError') {
            this.error = 'Accesso non completato entro il tempo previsto. Controlla la connessione e riprova.';
            return;
          }
          this.error = this.normalizeAuthError(
            apiErrorMessage(err, 'Credenziali non valide. Controlla email e password e riprova.'),
          );
        },
      });
  }

  register() {
    this.error = '';
    this.message = '';
    const fullName = this.registerFullName.trim();
    const email = this.registerEmail.trim();
    if (!fullName || !email || !this.registerPassword) {
      this.error = 'Compila nome completo, email e password per richiedere la registrazione.';
      return;
    }
    if (!this.isExpriviaEmail(email)) {
      this.error = DOMAIN_ERROR;
      return;
    }
    if (this.registerPassword.length < 8) {
      this.error = 'La password deve essere di almeno 8 caratteri.';
      return;
    }

    this.registering = true;
    this.registrationSuccessEmail = '';
    this.registrationSuccessOpen = false;

    this.auth
      .register({
        fullName,
        email,
        password: this.registerPassword,
        ruolo: 'GUEST',
      })
      .pipe(
        timeout(REQUEST_TIMEOUT_MS),
        finalize(() => {
          this.registering = false;
          this.renderNow();
        }),
      )
      .subscribe({
        next: () => {
          this.registrationSuccessEmail = email;
          this.registrationSuccessOpen = true;
          this.registerFullName = '';
          this.registerEmail = '';
          this.registerPassword = '';
          this.renderNow();
        },
        error: (err) => {
          if (err?.name === 'TimeoutError') {
            this.error = 'Registrazione non completata entro il tempo previsto. Controlla la connessione e riprova.';
            this.renderNow();
            return;
          }
          this.registrationSuccessOpen = false;
          this.error = this.normalizeRegistrationError(
            apiErrorMessage(err, 'Registrazione non riuscita. Riprova.'),
          );
          this.renderNow();
        },
      });
  }

  showLogin(): void {
    this.mode = 'login';
    this.error = '';
    this.message = '';
    this.forgotPasswordOpen = false;
    this.forgotPasswordEmail = '';
    this.registrationSuccessOpen = false;
  }

  showRegister(): void {
    this.mode = 'register';
    this.error = '';
    this.message = '';
    this.forgotPasswordOpen = false;
    this.forgotPasswordEmail = '';
    this.registrationSuccessOpen = false;
  }

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  toggleRegisterPasswordVisibility() {
    this.showRegisterPassword = !this.showRegisterPassword;
  }

  toggleForgotPassword(): void {
    this.error = '';
    this.message = '';
    this.forgotPasswordOpen = !this.forgotPasswordOpen;
    this.forgotPasswordEmail = this.forgotPasswordOpen ? this.email.trim() : '';
  }

  submitForgotPassword(): void {
    this.error = '';
    this.message = '';
    const email = this.forgotPasswordEmail.trim();
    if (!email) {
      this.error = 'Inserisci la tua email aziendale per inviare la richiesta di reset.';
      return;
    }
    if (!this.isExpriviaEmail(email)) {
      this.error = DOMAIN_ERROR;
      return;
    }

    this.forgotPasswordSubmitting = true;
    this.auth
      .requestPasswordReset(email)
      .pipe(
        timeout(REQUEST_TIMEOUT_MS),
        finalize(() => {
          this.forgotPasswordSubmitting = false;
          this.renderNow();
        }),
      )
      .subscribe({
        next: () => {
          this.message =
            "Se l'indirizzo e' valido, la richiesta e' stata registrata e verra presa in carico da un operatore.";
          this.forgotPasswordOpen = false;
          this.forgotPasswordEmail = '';
          this.renderNow();
        },
        error: (err) => {
          if (err?.name === 'TimeoutError') {
            this.error = 'Richiesta non completata entro il tempo previsto. Controlla la connessione e riprova.';
            this.renderNow();
            return;
          }
          this.error = apiErrorMessage(err, 'Invio richiesta non riuscito. Riprova.');
          this.renderNow();
        },
      });
  }

  goToLoginAfterRegistration(): void {
    this.registrationSuccessOpen = false;
    this.mode = 'login';
    this.email = this.registrationSuccessEmail;
    this.password = '';
    this.error = '';
    this.message = '';
    this.router.navigateByUrl('/', { replaceUrl: true });
  }

  private isExpriviaEmail(email: string): boolean {
    return EXPRIVIA_EMAIL_PATTERN.test(email);
  }

  private normalizeAuthError(message: string): string {
    if (message.toLowerCase().includes('credenziali non valide')) {
      return 'Credenziali non valide. Controlla email e password e riprova.';
    }
    if (message.includes('@exprivia.com')) {
      return DOMAIN_ERROR;
    }
    return message;
  }

  private normalizeRegistrationError(message: string): string {
    if (message.includes('@exprivia.com')) {
      return DOMAIN_ERROR;
    }
    if (message.toLowerCase().includes('gia') && message.toLowerCase().includes('registrata')) {
      return 'Email gia registrata. Accedi con le tue credenziali oppure utilizza un altro indirizzo aziendale.';
    }
    return message;
  }

  private renderNow(): void {
    try {
      this.changeDetector.detectChanges();
    } catch {
      // Il componente puo' essere gia stato distrutto dopo una navigazione.
    }
  }
}
