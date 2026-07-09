export type GroupType = 'HOUSEHOLD' | 'TRIP';
export type GroupRole = 'OWNER' | 'TREASURER' | 'MEMBER';

export interface CreateGroupRequest {
  name: string;
  type: GroupType;
  currency: string;
  startDate?: string | null;
  endDate?: string | null;
}

export interface GroupResponse {
  id: number;
  name: string;
  type: GroupType;
  currency: string;
  startDate: string | null;
  endDate: string | null;
  myRole: GroupRole;
}

export interface AddMemberRequest {
  email: string;
}

export interface MembershipResponse {
  userId: number;
  name: string;
  email: string;
  role: GroupRole;
  active: boolean;
}
