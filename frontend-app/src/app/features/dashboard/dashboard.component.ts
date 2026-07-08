import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard">
      <h1>Welcome, {{ authService.currentUser()?.name }}</h1>
      <p>Phase 1 complete: you're logged in and this route is protected by authGuard.</p>
      <button (click)="logout()">Log Out</button>
    </div>
  `,
})
export class DashboardComponent {
  constructor(public authService: AuthService, private router: Router) {}

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
