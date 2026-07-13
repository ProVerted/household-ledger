import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { GroupService } from '../../../core/services/group.service';
import { GroupContextService } from '../../../core/services/group-context.service';
import { GroupType, MembershipResponse } from '../../../core/models/group.model';

@Component({
  selector: 'app-group-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './group-list.component.html',
})
export class GroupListComponent implements OnInit {
  private fb = inject(FormBuilder);
  private groupService = inject(GroupService);
  groupContext = inject(GroupContextService);

  readonly isLoading = signal(true);
  readonly isCreating = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showCreateForm = signal(false);

  // Member management for whichever group is currently expanded.
  readonly expandedGroupId = signal<number | null>(null);
  readonly members = signal<MembershipResponse[]>([]);
  readonly isLoadingMembers = signal(false);
  readonly isAddingMember = signal(false);
  readonly memberErrorMessage = signal<string | null>(null);

  readonly createForm = this.fb.group({
    name: ['', [Validators.required]],
    type: ['HOUSEHOLD' as GroupType, [Validators.required]],
    currency: ['USD', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
    startDate: [''],
    endDate: [''],
    approvalThreshold: [100, [Validators.min(0)]],
  });

  readonly addMemberForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
  });

  ngOnInit(): void {
    this.loadGroups();
  }

  loadGroups(): void {
    this.isLoading.set(true);
    this.groupService.getMyGroups().subscribe({
      next: (groups) => {
        this.groupContext.setGroups(groups);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load your groups.');
        this.isLoading.set(false);
      },
    });
  }

  selectGroup(groupId: number): void {
    this.groupContext.setActiveGroup(groupId);
  }

  toggleMembers(groupId: number): void {
    if (this.expandedGroupId() === groupId) {
      this.expandedGroupId.set(null);
      return;
    }
    this.expandedGroupId.set(groupId);
    this.memberErrorMessage.set(null);
    this.addMemberForm.reset();
    this.loadMembers(groupId);
  }

  loadMembers(groupId: number): void {
    this.isLoadingMembers.set(true);
    this.groupService.getMembers(groupId).subscribe({
      next: (members) => {
        this.members.set(members);
        this.isLoadingMembers.set(false);
      },
      error: () => {
        this.memberErrorMessage.set('Could not load members.');
        this.isLoadingMembers.set(false);
      },
    });
  }

  onAddMember(groupId: number): void {
    if (this.addMemberForm.invalid) {
      this.addMemberForm.markAllAsTouched();
      return;
    }

    this.isAddingMember.set(true);
    this.memberErrorMessage.set(null);

    const email = this.addMemberForm.getRawValue().email!;

    this.groupService.addMember(groupId, { email }).subscribe({
      next: () => {
        this.isAddingMember.set(false);
        this.addMemberForm.reset();
        this.loadMembers(groupId);
      },
      error: (err: HttpErrorResponse) => {
        this.isAddingMember.set(false);
        this.memberErrorMessage.set(
          err.error?.message ?? 'Could not add member. Make sure they have an account already.'
        );
      },
    });
  }

  toggleCreateForm(): void {
    this.showCreateForm.update((v) => !v);
  }

  onCreateGroup(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    this.isCreating.set(true);
    this.errorMessage.set(null);

    const raw = this.createForm.getRawValue();

    this.groupService
      .createGroup({
        name: raw.name!,
        type: raw.type!,
        currency: raw.currency!.toUpperCase(),
        startDate: raw.type === 'TRIP' && raw.startDate ? raw.startDate : null,
        endDate: raw.type === 'TRIP' && raw.endDate ? raw.endDate : null,
        approvalThreshold: raw.approvalThreshold ?? 100,
      })
      .subscribe({
        next: () => {
          this.isCreating.set(false);
          this.showCreateForm.set(false);
          this.createForm.reset({ type: 'HOUSEHOLD', currency: 'USD' });
          this.loadGroups();
        },
        error: (err: HttpErrorResponse) => {
          this.isCreating.set(false);
          this.errorMessage.set(err.error?.message ?? 'Could not create group.');
        },
      });
  }
}
