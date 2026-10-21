import { Component, computed, input } from '@angular/core';

/**
 * Renders the same pill-shaped badge currently hand-coded per feature
 * (status-APPROVED/PENDING_APPROVAL/REJECTED in expense-list, the
 * active-badge in group-list, the .inactive row styling in recurring-list).
 * One component + one style block, instead of every list re-deriving its
 * own status -> class -> color mapping.
 *
 * Usage:
 *   <app-status-badge [status]="expense.status" />
 *   <app-status-badge status="ACTIVE" />
 *   <app-status-badge status="INACTIVE" />
 */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `<span class="status-badge" [class]="'status-' + status()">{{ label() }}</span>`,
})
export class StatusBadgeComponent {
  status = input.required<string>();

  // Falls back to the raw status string for any value not in this map,
  // so a new backend status doesn't silently render blank - it just
  // shows the raw enum text with the default badge style until someone
  // adds a proper label/color for it.
  private readonly labels: Record<string, string> = {
    APPROVED: 'Approved',
    PENDING_APPROVAL: 'Pending approval',
    REJECTED: 'Rejected',
    ACTIVE: 'Active',
    INACTIVE: 'Deactivated',
  };

  label = computed(() => this.labels[this.status()] ?? this.status());
}