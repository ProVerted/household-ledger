import { signal } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

/**
 * Every list component (ExpenseList, GroupList, RecurringList, and
 * BalanceSummary) independently declares its own `isLoading` signal,
 * `errorMessage` signal, and a subscribe({ next, error }) block that sets
 * both. This wraps that exact pattern once.
 *
 * It's a plain class, not a component - list templates differ too much
 * (cards vs table vs expandable panels) to force through one component,
 * but the load/loading/error bookkeeping around them doesn't need to.
 *
 * Usage (replaces the isLoading/errorMessage signals + subscribe block in
 * ExpenseListComponent.loadExpenses, GroupListComponent, etc.):
 *
 *   readonly expensesState = new AsyncListState<ExpenseResponse[]>();
 *
 *   loadExpenses(): void {
 *     const groupId = this.groupContext.activeGroup()?.id;
 *     if (!groupId) { this.expensesState.isLoading.set(false); return; }
 *     this.expensesState.load(
 *       this.expenseService.getExpenses(groupId),
 *       'Could not load expenses.'
 *     );
 *   }
 *
 * Template:
 *   @if (expensesState.isLoading()) { <p>Loading...</p> }
 *   @else if (expensesState.errorMessage()) { <p>{{ expensesState.errorMessage() }}</p> }
 *   @else if (expensesState.data()?.length === 0) { <p>Nothing here yet.</p> }
 *   @else { ...render expensesState.data()... }
 */
export class AsyncListState<T> {
  readonly data = signal<T | null>(null);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  load(source$: Observable<T>, fallbackErrorMessage: string): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    source$.subscribe({
      next: (value) => {
        this.data.set(value);
        this.isLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(err.error?.message ?? fallbackErrorMessage);
        this.isLoading.set(false);
      },
    });
  }
}