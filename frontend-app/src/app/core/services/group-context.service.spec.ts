import { TestBed } from '@angular/core/testing';
import { GroupContextService } from './group-context.service';
import { GroupResponse } from '../models/group.model';

describe('GroupContextService', () => {
  let service: GroupContextService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [GroupContextService],
    });
    service = TestBed.inject(GroupContextService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should initialize with no active group when localStorage is empty', () => {
    expect(service.groups()).toEqual([]);
    expect(service.activeGroup()).toBeNull();
  });

  it('should set groups and default to the first group if nothing was active', () => {
    const dummyGroups: GroupResponse[] = [
      {
        id: 1,
        name: 'Household',
        type: 'HOUSEHOLD',
        currency: 'USD',
        startDate: null,
        endDate: null,
        approvalThreshold: 0,
        myRole: 'OWNER',
      },
      {
        id: 2,
        name: 'Summer Trip',
        type: 'TRIP',
        currency: 'EUR',
        startDate: '2026-06-01',
        endDate: '2026-06-10',
        approvalThreshold: 50,
        myRole: 'MEMBER',
      },
    ];

    service.setGroups(dummyGroups);

    expect(service.groups().length).toBe(2);
    expect(service.activeGroup()?.id).toBe(1);
    expect(service.activeGroup()?.name).toBe('Household');
  });

  it('should switch active group and persist ID in localStorage', () => {
    const dummyGroups: GroupResponse[] = [
      {
        id: 1,
        name: 'Household',
        type: 'HOUSEHOLD',
        currency: 'USD',
        startDate: null,
        endDate: null,
        approvalThreshold: 0,
        myRole: 'OWNER',
      },
      {
        id: 2,
        name: 'Summer Trip',
        type: 'TRIP',
        currency: 'EUR',
        startDate: '2026-06-01',
        endDate: '2026-06-10',
        approvalThreshold: 50,
        myRole: 'MEMBER',
      },
    ];

    service.setGroups(dummyGroups);
    service.setActiveGroup(2);

    expect(service.activeGroup()?.id).toBe(2);
    expect(service.activeGroup()?.name).toBe('Summer Trip');
    expect(localStorage.getItem('active_group_id')).toBe('2');
  });

  it('should clear all groups and active state on clear()', () => {
    const dummyGroups: GroupResponse[] = [
      {
        id: 1,
        name: 'Household',
        type: 'HOUSEHOLD',
        currency: 'USD',
        startDate: null,
        endDate: null,
        approvalThreshold: 0,
        myRole: 'OWNER',
      },
    ];

    service.setGroups(dummyGroups);
    service.clear();

    expect(service.groups()).toEqual([]);
    expect(service.activeGroup()).toBeNull();
    expect(localStorage.getItem('active_group_id')).toBeNull();
  });
});
