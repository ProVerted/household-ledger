import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateRecurringExpenseRequest, RecurringExpenseResponse } from '../models/recurring-expense.model';

@Injectable({ providedIn: 'root' })
export class RecurringExpenseService {
  private readonly baseUrl = 'http://localhost:8080/api/groups';

  constructor(private http: HttpClient) {}

  getTemplates(groupId: number): Observable<RecurringExpenseResponse[]> {
    return this.http.get<RecurringExpenseResponse[]>(`${this.baseUrl}/${groupId}/recurring`);
  }

  createTemplate(
    groupId: number,
    request: CreateRecurringExpenseRequest
  ): Observable<RecurringExpenseResponse> {
    return this.http.post<RecurringExpenseResponse>(`${this.baseUrl}/${groupId}/recurring`, request);
  }

  deactivate(groupId: number, templateId: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${groupId}/recurring/${templateId}/deactivate`, {});
  }
}