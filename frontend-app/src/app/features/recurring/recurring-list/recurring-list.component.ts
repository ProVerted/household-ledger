import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { GroupContextService } from '../../../core/services/group-context.service';
import { GroupService } from '../../../core/services/group.service';
import { RecurringExpenseService } from '../../../core/services/recurring-expense.service';
import { RecurringExpenseResponse } from '../../../core/models/recurring-expense.model';
import { MembershipResponse } from '../../../core/models/group.model';

@Component({
  selector: 'app-recurring-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './recurring-list.component.html',
})
export class RecurringListComponent implements OnInit {
  private fb = inject(FormBuilder);
  private recurringExpenseService = inject(RecurringExpenseService);
  private groupService = inject(GroupService);
  groupContext = inject(GroupContextService);

  readonly templates = signal<RecurringExpenseResponse[]>([]);
  readonly members = signal<MembershipResponse[]>([]);
  readonly isLoading = signal(true);
  readonly isCreating = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showCreateForm = signal(false);
  readonly processingTemplateId = signal<number | null>(null);

  get canManage(): boolean {
    const role = this.groupContext.activeGroup()?.myRole;
    return role === 'OWNER' || role === 'TREASURER';
  }

  readonly createForm = this.fb.group({
    description: ['', [Validators.required]],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    currency: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
    category: [''],
    dayOfMonth: [1, [Validators.required, Validators.min(1), Validators.max(31)]],
    paidByUserId: [null as number | null],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const groupId = this.groupContext.activeGroup()?.id;
    if (!groupId) {
      this.isLoading.set(false);
      return;
    }

    // Default the currency field + form defaults to the active group's own
    // currency, and pre-fill the paidBy dropdown once so the form isn't empty
    // the first time someone opens it.
    const activeGroup = this.groupContext.activeGroup();
    if (activeGroup) {
      this.createForm.patchValue({ currency: activeGroup.currency });
    }

    this.isLoading.set(true);
    this.recurringExpenseService.getTemplates(groupId).subscribe({
      next: (templates) => {
        this.templates.set(templates);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load recurring expenses.');
        this.isLoading.set(false);
      },
    });

    this.groupService.getMembers(groupId).subscribe({
      next: (members) => this.members.set(members.filter((m) => m.active)),
      error: () => {
        // Non-fatal - the paidBy dropdown just falls back to "yourself" (the default).
      },
    });
  }

  toggleCreateForm(): void {
    this.showCreateForm.update((v) => !v);
  }

  onCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    const groupId = this.groupContext.activeGroup()?.id;
    if (!groupId) return;

    this.isCreating.set(true);
    this.errorMessage.set(null);

    const raw = this.createForm.getRawValue();

    this.recurringExpenseService
      .createTemplate(groupId, {
        description: raw.description!,
        amount: raw.amount!,
        currency: raw.currency!.toUpperCase(),
        category: raw.category || null,
        dayOfMonth: raw.dayOfMonth!,
        paidByUserId: raw.paidByUserId,
      })
      .subscribe({
        next: () => {
          this.isCreating.set(false);
          this.showCreateForm.set(false);
          this.createForm.reset({ amount: 0, dayOfMonth: 1, currency: this.groupContext.activeGroup()?.currency });
          this.load();
        },
        error: (err: HttpErrorResponse) => {
          this.isCreating.set(false);
          this.errorMessage.set(err.error?.message ?? 'Could not create recurring expense.');
        },
      });
  }

  deactivate(templateId: number): void {
    const groupId = this.groupContext.activeGroup()?.id;
    if (!groupId) return;

    this.processingTemplateId.set(templateId);
    this.recurringExpenseService.deactivate(groupId, templateId).subscribe({
      next: () => {
        this.processingTemplateId.set(null);
        this.load();
      },
      error: () => {
        this.processingTemplateId.set(null);
        this.errorMessage.set('Could not deactivate this recurring expense.');
      },
    });
  }
}