import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AddMemberRequest,
  CreateGroupRequest,
  GroupResponse,
  MembershipResponse,
} from '../models/group.model';

@Injectable({ providedIn: 'root' })
export class GroupService {
  private readonly baseUrl = 'http://localhost:8080/api/groups';

  constructor(private http: HttpClient) {}

  createGroup(request: CreateGroupRequest): Observable<GroupResponse> {
    return this.http.post<GroupResponse>(this.baseUrl, request);
  }

  getMyGroups(): Observable<GroupResponse[]> {
    return this.http.get<GroupResponse[]>(`${this.baseUrl}/mine`);
  }

  getMembers(groupId: number): Observable<MembershipResponse[]> {
    return this.http.get<MembershipResponse[]>(`${this.baseUrl}/${groupId}/members`);
  }

  addMember(groupId: number, request: AddMemberRequest): Observable<MembershipResponse> {
    return this.http.post<MembershipResponse>(`${this.baseUrl}/${groupId}/members`, request);
  }

  leaveGroup(groupId: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${groupId}/leave`, {});
  }
}
