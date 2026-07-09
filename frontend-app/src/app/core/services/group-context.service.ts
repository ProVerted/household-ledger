import { Injectable, computed, signal } from '@angular/core';
import { GroupResponse } from '../models/group.model';

const ACTIVE_GROUP_ID_KEY = 'active_group_id';

/**
 * Central place for "which group is active right now."
 *
 * Any component that injects this and reads `activeGroup()` reactively updates
 * the moment the user switches groups elsewhere in the app - no manual refresh
 * calls needed, since components using it in a template respond to Signal changes
 * automatically.
 */
@Injectable({ providedIn: 'root' })
export class GroupContextService {
  private readonly groupsSignal = signal<GroupResponse[]>([]);
  private readonly activeGroupIdSignal = signal<number | null>(this.loadStoredActiveGroupId());

  readonly groups = this.groupsSignal.asReadonly();

  readonly activeGroup = computed(() => {
    const id = this.activeGroupIdSignal();
    return this.groupsSignal().find((g) => g.id === id) ?? null;
  });

  setGroups(groups: GroupResponse[]): void {
    this.groupsSignal.set(groups);

    // If nothing is active yet (or the previously active group no longer exists
    // for this user, e.g. they left it), default to the first available group.
    const currentActiveId = this.activeGroupIdSignal();
    const stillValid = groups.some((g) => g.id === currentActiveId);
    if (!stillValid && groups.length > 0) {
      this.setActiveGroup(groups[0].id);
    }
  }

  setActiveGroup(groupId: number): void {
    this.activeGroupIdSignal.set(groupId);
    localStorage.setItem(ACTIVE_GROUP_ID_KEY, String(groupId));
  }

  clear(): void {
    this.groupsSignal.set([]);
    this.activeGroupIdSignal.set(null);
    localStorage.removeItem(ACTIVE_GROUP_ID_KEY);
  }

  private loadStoredActiveGroupId(): number | null {
    const raw = localStorage.getItem(ACTIVE_GROUP_ID_KEY);
    return raw ? Number(raw) : null;
  }
}
