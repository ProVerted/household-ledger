import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { GroupContextService } from '../../core/services/group-context.service';
import { GroupService } from '../../core/services/group.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="dashboard">
      <h1>Welcome, {{ authService.currentUser()?.name }}</h1>

      @if (groupContext.activeGroup(); as active) {
        <p>
          Active group: <strong>{{ active.name }}</strong>
          ({{ active.type }} · {{ active.currency }} · your role: {{ active.myRole }})
        </p>
      } @else {
        <p>You don't have an active group yet.</p>
      }

      <p><a routerLink="/groups">Manage / switch groups</a></p>

      <button (click)="logout()">Log Out</button>
    </div>
  `,
})
export class DashboardComponent implements OnInit {
  authService = inject(AuthService);
  groupContext = inject(GroupContextService);
  private groupService = inject(GroupService);
  private router = inject(Router);

  ngOnInit(): void {
    // Load groups on dashboard entry so the active-group summary has data,
    // even if the user hasn't visited /groups yet this session.
    this.groupService.getMyGroups().subscribe({
      next: (groups) => this.groupContext.setGroups(groups),
      error: () => {
        // Non-fatal for the dashboard - the groups page will surface the real error.
      },
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
