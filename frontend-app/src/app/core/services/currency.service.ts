import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CurrencyRate, SupportedCurrency } from '../models/currency.model';

@Injectable({ providedIn: 'root' })
export class CurrencyService {
  private http = inject(HttpClient);

  getSupportedCurrencies(): Observable<SupportedCurrency[]> {
    return this.http.get<SupportedCurrency[]>('/api/currencies');
  }

  getRate(from: string, to: string): Observable<CurrencyRate> {
    return this.http.get<CurrencyRate>(`/api/currencies/rate?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
  }
}
