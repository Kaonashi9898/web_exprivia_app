import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { apiErrorMessage } from '../../core/api-error.utils';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-home',
  imports: [FormsModule],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class HomeComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  email = '';
  password = '';
  loading = false;
  error = '';
  showPassword = false;

  scrollTo(sectionId: string) {
    const section = document.getElementById(sectionId);
    if (section) {
      section.scrollIntoView({ behavior: 'smooth' });
    }
  }

  login() {
    this.error = '';
    this.loading = true;

    this.auth
      .login(this.email.trim(), this.password)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: () => this.router.navigateByUrl('/dashboard'),
        error: (err) => {
          this.error = apiErrorMessage(err, 'Credenziali non valide o servizio non raggiungibile.');
        },
      });
  }

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }
}
