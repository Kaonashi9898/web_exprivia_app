import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { RuoloUtente } from './app.models';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/']);
};

export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const roles = (route.data['roles'] ?? []) as RuoloUtente[];

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/']);
  }

  if (!roles.length || auth.hasAnyRole(roles)) {
    return true;
  }

  return router.createUrlTree(['/dashboard']);
};
