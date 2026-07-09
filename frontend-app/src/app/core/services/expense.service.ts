import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateExpenseRequest, ExpenseResponse, UpdateExpenseRequest } from '../models/expense.model';

@Injectable({ providedIn: 'root' })
export class ExpenseService {
  private readonly baseUrl = 'http://localhost:8080/api/groups';

  constructor(private http: HttpClient) {}

  createExpense(groupId: number, request: CreateExpenseRequest): Observable<ExpenseResponse> {
    return this.http.post<ExpenseResponse>(`${this.baseUrl}/${groupId}/expenses`, request);
  }

  getExpenses(groupId: number): Observable<ExpenseResponse[]> {
    return this.http.get<ExpenseResponse[]>(`${this.baseUrl}/${groupId}/expenses`);
  }

  getExpense(groupId: number, expenseId: number): Observable<ExpenseResponse> {
    return this.http.get<ExpenseResponse>(`${this.baseUrl}/${groupId}/expenses/${expenseId}`);
  }

  updateExpense(
    groupId: number,
    expenseId: number,
    request: UpdateExpenseRequest
  ): Observable<ExpenseResponse> {
    return this.http.patch<ExpenseResponse>(`${this.baseUrl}/${groupId}/expenses/${expenseId}`, request);
  }
}
