import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
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
        this.errorMessage.set(err.error?.message ?? 'Could not approve expense.');
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
        this.errorMessage.set(err.error?.message ?? 'Could not reject expense.');
      },
    });
  }
}
