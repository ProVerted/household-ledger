import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { GroupContextService } from '../../core/services/group-context.service';
import { SettlementService } from '../../core/services/settlement.service';
import {
  BalanceResponse,
  SettlementSuggestionResponse,
  SettlementTransactionResponse,
} from '../../core/models/settlement.model';

@Component({
  selector: 'app-balance-summary',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './balance-summary.component.html',
})
export class BalanceSummaryComponent implements OnInit {
  private settlementService = inject(SettlementService);
  groupContext = inject(GroupContextService);

  readonly balances = signal<BalanceResponse[]>([]);
  readonly suggestions = signal<SettlementSuggestionResponse[]>([]);
  readonly history = signal<SettlementTransactionResponse[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly confirmingIndex = signal<number | null>(null);

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    const groupId = this.groupContext.activeGroup()?.id;
    if (!groupId) {
      this.isLoading.set(false);
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.settlementService.getBalances(groupId).subscribe({
      next: (balances) => this.balances.set(balances),
      error: () => this.errorMessage.set('Could not load balances.'),
    });

    this.settlementService.getSuggestedSettlements(groupId).subscribe({
      next: (suggestions) => {
        this.suggestions.set(suggestions);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load settlement suggestions.');
        this.isLoading.set(false);
      },
    });

    this.settlementService.getHistory(groupId).subscribe({
      next: (history) => this.history.set(history),
      error: () => {
        // Non-fatal - history is supplementary, the suggestions above are the main feature.
      },
    });
  }

  confirmSettlement(suggestion: SettlementSuggestionResponse, index: number): void {
    const groupId = this.groupContext.activeGroup()?.id;
    if (!groupId) return;

    this.confirmingIndex.set(index);

    this.settlementService
      .confirmSettlement(groupId, {
        fromUserId: suggestion.fromUserId,
        toUserId: suggestion.toUserId,
        amount: suggestion.amount,
      })
      .subscribe({
        next: () => {
          this.confirmingIndex.set(null);
          // Balances now DO reflect confirmed settlements, so the whole picture
          // needs refreshing - the settled suggestion should disappear from the
          // list entirely rather than staying there to be clicked again.
          this.loadAll();
        },
        error: (err: HttpErrorResponse) => {
          this.confirmingIndex.set(null);
          this.errorMessage.set(err.error?.message ?? 'Could not confirm settlement.');
        },
      });
  }
}
