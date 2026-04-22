import { Routes } from '@angular/router';
import { HomeComponent } from './pages/home/home';
import { AppShellComponent } from './components/app-shell/app-shell';
import { authGuard, roleGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },
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
        loadComponent: () => import('./pages/locations/locations').then((m) => m.LocationsComponent),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'RECEPTION', 'USER'] }
      },
      {
        path: 'utenti',
        loadComponent: () => import('./pages/users/users').then((m) => m.UsersComponent),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] }
      },
      {
        path: 'sedi-postazioni',
        loadComponent: () => import('./pages/bookings/bookings').then((m) => m.BookingsComponent),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'RECEPTION', 'USER'] }
      },
      {
        path: 'planimetria',
        loadComponent: () => import('./pages/floor-plan/floor-plan').then((m) => m.FloorPlanComponent),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'BUILDING_MANAGER'] }
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
