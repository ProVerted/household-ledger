import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
    BalanceResponse,
    ConfirmSettlementRequest,
    SettlementSuggestionResponse,
    SettlementTransactionResponse,
} from '../models/settlement.model';

@Injectable({ providedIn: 'root' })
export class SettlementService {
  private readonly baseUrl = 'http://localhost:8080/api/groups';

  constructor(private http: HttpClient) {}

  getBalances(groupId: number): Observable<BalanceResponse[]> {
    return this.http.get<BalanceResponse[]>(`${this.baseUrl}/${groupId}/balances`);
  }

  getSuggestedSettlements(groupId: number): Observable<SettlementSuggestionResponse[]> {
    return this.http.get<SettlementSuggestionResponse[]>(
      `${this.baseUrl}/${groupId}/settlements/suggested`
    );
  }

  confirmSettlement(
    groupId: number,
    request: ConfirmSettlementRequest
  ): Observable<SettlementTransactionResponse> {
    return this.http.post<SettlementTransactionResponse>(
      `${this.baseUrl}/${groupId}/settlements/confirm`,
      request
    );
  }

  getHistory(groupId: number): Observable<SettlementTransactionResponse[]> {
    return this.http.get<SettlementTransactionResponse[]>(`${this.baseUrl}/${groupId}/settlements/history`);
  }
}
