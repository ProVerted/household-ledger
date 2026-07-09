import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'groups',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/groups/group-list/group-list.component').then((m) => m.GroupListComponent),
  },
  {
    path: 'expenses',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/expenses/expense-list/expense-list.component').then((m) => m.ExpenseListComponent),
  },
  {
    path: 'expenses/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/expenses/expense-form/expense-form.component').then((m) => m.ExpenseFormComponent),
  },
  { path: '**', redirectTo: 'login' },
];
