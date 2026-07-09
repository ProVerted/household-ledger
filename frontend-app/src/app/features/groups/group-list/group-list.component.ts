import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { GroupService } from '../../../core/services/group.service';
import { GroupContextService } from '../../../core/services/group-context.service';
import { GroupType } from '../../../core/models/group.model';

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

  readonly createForm = this.fb.group({
    name: ['', [Validators.required]],
    type: ['HOUSEHOLD' as GroupType, [Validators.required]],
    currency: ['USD', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
    startDate: [''],
    endDate: [''],
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
