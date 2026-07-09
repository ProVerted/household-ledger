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
import { MembershipResponse } from '../../../core/models/group.model';
import { SplitType } from '../../../core/models/expense.model';

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
  private router = inject(Router);
  groupContext = inject(GroupContextService);

  readonly members = signal<MembershipResponse[]>([]);
  readonly isLoadingMembers = signal(true);
  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group(
    {
      description: ['', [Validators.required]],
      amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
      currency: ['USD', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
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

    this.form.patchValue({ currency: this.groupContext.activeGroup()?.currency ?? 'USD' });

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
    // Re-run validation since switching split type changes what "valid" means
    // for the exact same row values (e.g. 100 might be fine for percentage,
    // meaningless for exact).
    this.form.updateValueAndValidity();
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

    this.expenseService
      .createExpense(groupId, {
        description: raw.description!,
        amount: raw.amount!,
        currency: raw.currency!.toUpperCase(),
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
