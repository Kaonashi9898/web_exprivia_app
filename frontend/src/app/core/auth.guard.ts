import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { RuoloUtente } from './app.models';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.ensureProfile().pipe(
    map((user) => user ? true : router.createUrlTree(['/'])),
  );
};

export const publicHomeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.currentUser();
  return of(user ? router.createUrlTree(['/dashboard']) : true);
};

export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const roles = (route.data['roles'] ?? []) as RuoloUtente[];

  return auth.ensureProfile().pipe(
    map((user) => {
      if (!user) {
        return router.createUrlTree(['/']);
      }

      return !roles.length || roles.includes(user.ruolo)
        ? true
        : router.createUrlTree(['/dashboard']);
    }),
  );
};
