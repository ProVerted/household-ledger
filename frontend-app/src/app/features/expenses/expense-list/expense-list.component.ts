import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { GroupContextService } from '../../../core/services/group-context.service';
import { ExpenseService } from '../../../core/services/expense.service';
import { ExpenseResponse } from '../../../core/models/expense.model';

@Component({
  selector: 'app-expense-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './expense-list.component.html',
})
export class ExpenseListComponent implements OnInit {
  private expenseService = inject(ExpenseService);
  groupContext = inject(GroupContextService);

  readonly expenses = signal<ExpenseResponse[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadExpenses();
  }

  loadExpenses(): void {
    const groupId = this.groupContext.activeGroup()?.id;
    if (!groupId) {
      this.isLoading.set(false);
      return;
    }

    this.isLoading.set(true);
    this.expenseService.getExpenses(groupId).subscribe({
      next: (expenses) => {
        this.expenses.set(expenses);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load expenses.');
        this.isLoading.set(false);
      },
    });
  }
}
