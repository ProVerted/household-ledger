import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { GroupContextService } from '../../../core/services/group-context.service';
import { ExpenseService } from '../../../core/services/expense.service';
import { ExpenseResponse } from '../../../core/models/expense.model';
import { AsyncListState } from '../../../shared/async-state/async-list-state';
import { StatusBadgeComponent } from '../../../shared/status-badge/status-badge.component';

@Component({
  selector: 'app-expense-list',
  standalone: true,
  imports: [CommonModule, RouterLink, StatusBadgeComponent],
  templateUrl: './expense-list.component.html',
})
export class ExpenseListComponent implements OnInit {
  private expenseService = inject(ExpenseService);
  groupContext = inject(GroupContextService);

  readonly expensesState = new AsyncListState<ExpenseResponse[]>();
  readonly processingExpenseId = signal<number | null>(null);

  get canApprove(): boolean {
    const role = this.groupContext.activeGroup()?.myRole;
    return role === 'OWNER' || role === 'TREASURER';
  }

  ngOnInit(): void {
    this.loadExpenses();
  }

  loadExpenses(): void {
    const groupId = this.groupContext.activeGroup()?.id;
    if (!groupId) {
      this.expensesState.isLoading.set(false);
      return;
    }

    this.expensesState.load(
      this.expenseService.getExpenses(groupId),
      'Could not load expenses.'
    );
  }

  approve(expenseId: number): void {
    const groupId = this.groupContext.activeGroup()?.id;
    if (!groupId) return;

    this.processingExpenseId.set(expenseId);
    this.expenseService.approveExpense(groupId, expenseId).subscribe({
      next: () => {
        this.processingExpenseId.set(null);
        this.loadExpenses();
      },
      error: (err: HttpErrorResponse) => {
        this.processingExpenseId.set(null);
        this.expensesState.errorMessage.set(err.error?.message ?? 'Could not approve expense.');
      },
    });
  }

  reject(expenseId: number): void {
    const groupId = this.groupContext.activeGroup()?.id;
    if (!groupId) return;

    this.processingExpenseId.set(expenseId);
    this.expenseService.rejectExpense(groupId, expenseId).subscribe({
      next: () => {
        this.processingExpenseId.set(null);
        this.loadExpenses();
      },
      error: (err: HttpErrorResponse) => {
        this.processingExpenseId.set(null);
        this.expensesState.errorMessage.set(err.error?.message ?? 'Could not reject expense.');
      },
    });
  }
}