import { Routes } from '@angular/router';
import { HomeComponent } from './pages/home/home';
import { AppShellComponent } from './components/app-shell/app-shell';
import { authGuard, publicHomeGuard, roleGuard } from './core/auth.guard';
import {
  BOOKING_ROLES,
  LOCATION_MANAGEMENT_ROLES,
  PLAN_EDITOR_ROLES,
  USER_MANAGEMENT_ROLES,
} from './core/role-access';

export const routes: Routes = [
  { path: '', component: HomeComponent, canActivate: [publicHomeGuard] },
  {
    path: '',
    component: AppShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.DashboardComponent)
      },
      {
        path: 'prenotazioni',
        loadComponent: () => import('./pages/prenotazioni/prenotazioni').then((m) => m.PrenotazioniComponent),
        canActivate: [roleGuard],
        data: { roles: BOOKING_ROLES }
      },
      {
        path: 'utenti',
        loadComponent: () => import('./pages/users/users').then((m) => m.UsersComponent),
        canActivate: [roleGuard],
        data: { roles: USER_MANAGEMENT_ROLES }
      },
      {
        path: 'sedi-postazioni',
        loadComponent: () => import('./pages/sedi-postazioni/sedi-postazioni').then((m) => m.SediPostazioniComponent),
        canActivate: [roleGuard],
        data: { roles: LOCATION_MANAGEMENT_ROLES }
      },
      {
        path: 'planimetria',
        loadComponent: () => import('./pages/floor-plan/floor-plan').then((m) => m.FloorPlanComponent),
        canActivate: [roleGuard],
        data: { roles: PLAN_EDITOR_ROLES }
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
