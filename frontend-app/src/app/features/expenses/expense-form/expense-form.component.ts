import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { GroupContextService } from '../../../core/services/group-context.service';
import { GroupService } from '../../../core/services/group.service';
import { ExpenseService } from '../../../core/services/expense.service';
import { AuthService } from '../../../core/services/auth.service';
import { CurrencyService } from '../../../core/services/currency.service';
import { MembershipResponse } from '../../../core/models/group.model';
import { SplitType } from '../../../core/models/expense.model';
import { SupportedCurrency } from '../../../core/models/currency.model';

// Cross-field validator: what "valid splits" means depends entirely on splitType,
// so this has to look at splitType, amount, and every split row together -
// no single field's own validators could express this on their own.
function splitsValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const splitType: SplitType = control.get('splitType')?.value;
    const amount: number = Number(control.get('amount')?.value) || 0;
    const splitsArray = control.get('splits') as FormArray;
    if (!splitsArray) return null;

    const rows = splitsArray.controls.map((c) => c.value);
    const included = rows.filter((r) => r.include);

    if (included.length === 0) {
      return { noSplitsSelected: true };
    }

    if (splitType === 'PERCENTAGE') {
      const sum = included.reduce((acc, r) => acc + (Number(r.value) || 0), 0);
      if (Math.abs(sum - 100) > 0.5) {
        return { percentageMismatch: { actual: sum } };
      }
    }

    if (splitType === 'EXACT') {
      const sum = included.reduce((acc, r) => acc + (Number(r.value) || 0), 0);
      if (Math.abs(sum - amount) > 0.02) {
        return { exactMismatch: { actual: sum, expected: amount } };
      }
    }

    return null;
  };
}

@Component({
  selector: 'app-expense-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './expense-form.component.html',
})
export class ExpenseFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private groupService = inject(GroupService);
  private expenseService = inject(ExpenseService);
  private authService = inject(AuthService);
  private currencyService = inject(CurrencyService);
  private router = inject(Router);
  groupContext = inject(GroupContextService);

  readonly members = signal<MembershipResponse[]>([]);
  readonly isLoadingMembers = signal(true);
  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly supportedCurrencies = signal<SupportedCurrency[]>([
    { code: 'USD', name: 'US Dollar', symbol: '$' },
    { code: 'EUR', name: 'Euro', symbol: '€' },
    { code: 'GBP', name: 'British Pound', symbol: '£' },
    { code: 'CAD', name: 'Canadian Dollar', symbol: 'CA$' },
    { code: 'AUD', name: 'Australian Dollar', symbol: 'A$' },
    { code: 'JPY', name: 'Japanese Yen', symbol: '¥' },
    { code: 'CHF', name: 'Swiss Franc', symbol: 'CHF' },
    { code: 'INR', name: 'Indian Rupee', symbol: '₹' },
    { code: 'CNY', name: 'Chinese Yuan', symbol: '¥' },
    { code: 'SGD', name: 'Singapore Dollar', symbol: 'S$' },
    { code: 'NZD', name: 'New Zealand Dollar', symbol: 'NZ$' },
  ]);

  readonly activeExchangeRate = signal<number | null>(null);
  readonly estimatedBaseAmount = signal<number | null>(null);
  readonly isForeignCurrency = signal(false);
  readonly showCustomRate = signal(false);

  readonly form = this.fb.group(
    {
      description: ['', [Validators.required]],
      amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
      currency: ['USD', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
      customExchangeRate: [null as number | null, [Validators.min(0.000001)]],
      category: [''],
      expenseDate: [new Date().toISOString().substring(0, 10), [Validators.required]],
      paidByUserId: [null as number | null, [Validators.required]],
      splitType: ['EQUAL' as SplitType, [Validators.required]],
      splits: this.fb.array([]),
    },
    { validators: splitsValidator() }
  );

  get splitsArray(): FormArray {
    return this.form.get('splits') as FormArray;
  }

  ngOnInit(): void {
    const groupId = this.groupContext.activeGroup()?.id;
    if (!groupId) {
      this.router.navigate(['/groups']);
      return;
    }

    const baseCurrency = this.groupContext.activeGroup()?.currency ?? 'USD';
    this.form.patchValue({ currency: baseCurrency });

    this.currencyService.getSupportedCurrencies().subscribe({
      next: (list) => {
        if (list && list.length > 0) {
          this.supportedCurrencies.set(list);
        }
      },
      error: () => {
        // Fallback list is already populated
      },
    });

    this.groupService.getMembers(groupId).subscribe({
      next: (members) => {
        this.members.set(members);
        this.buildSplitRows(members);

        const currentUserId = this.authService.currentUser()?.userId;
        const defaultPayer = members.find((m) => m.userId === currentUserId) ?? members[0];
        if (defaultPayer) {
          this.form.patchValue({ paidByUserId: defaultPayer.userId });
        }

        this.isLoadingMembers.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load group members.');
        this.isLoadingMembers.set(false);
      },
    });
  }

  private buildSplitRows(members: MembershipResponse[]): void {
    members.forEach((member) => {
      this.splitsArray.push(
        this.fb.group({
          userId: [member.userId],
          name: [member.name],
          include: [true],
          value: [null as number | null],
        })
      );
    });
  }

  onSplitTypeChange(): void {
    this.form.updateValueAndValidity();
  }

  onCurrencyOrAmountChange(): void {
    const enteredCurrency = (this.form.get('currency')?.value || '').trim().toUpperCase();
    const baseCurrency = (this.groupContext.activeGroup()?.currency || 'USD').trim().toUpperCase();
    const amount = Number(this.form.get('amount')?.value) || 0;
    const customRate = Number(this.form.get('customExchangeRate')?.value) || null;

    if (enteredCurrency && enteredCurrency !== baseCurrency) {
      this.isForeignCurrency.set(true);

      if (customRate && customRate > 0) {
        this.activeExchangeRate.set(customRate);
        this.estimatedBaseAmount.set(Math.round(amount * customRate * 100) / 100);
      } else {
        this.currencyService.getRate(enteredCurrency, baseCurrency).subscribe({
          next: (rateData) => {
            this.activeExchangeRate.set(rateData.rate);
            this.estimatedBaseAmount.set(Math.round(amount * rateData.rate * 100) / 100);
          },
          error: () => {
            // Default 1:1 if rate lookup unavailable
            this.activeExchangeRate.set(1);
            this.estimatedBaseAmount.set(amount);
          },
        });
      }
    } else {
      this.isForeignCurrency.set(false);
      this.activeExchangeRate.set(1);
      this.estimatedBaseAmount.set(amount);
    }
  }

  toggleCustomRate(): void {
    this.showCustomRate.update((v) => !v);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const groupId = this.groupContext.activeGroup()?.id;
    if (!groupId) return;

    this.isSubmitting.set(true);
    this.errorMessage.set(null);

    const raw = this.form.getRawValue();
    const splitType: SplitType = raw.splitType!;

    const splits = (raw.splits as any[])
      .filter((row) => row.include)
      .map((row) => ({
        userId: row.userId,
        value: splitType === 'EQUAL' ? null : Number(row.value),
      }));

    const customRate = raw.customExchangeRate ? Number(raw.customExchangeRate) : null;

    this.expenseService
      .createExpense(groupId, {
        description: raw.description!,
        amount: raw.amount!,
        currency: raw.currency!.trim().toUpperCase(),
        exchangeRate: customRate,
        category: raw.category || null,
        expenseDate: raw.expenseDate!,
        paidByUserId: raw.paidByUserId,
        splitType,
        splits,
      })
      .subscribe({
        next: () => {
          this.isSubmitting.set(false);
          this.router.navigate(['/expenses']);
        },
        error: (err: HttpErrorResponse) => {
          this.isSubmitting.set(false);
          this.errorMessage.set(err.error?.message ?? 'Could not create expense.');
        },
      });
  }
}
