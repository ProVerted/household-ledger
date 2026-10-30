import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CurrencyService } from './currency.service';
import { CurrencyRate, SupportedCurrency } from '../models/currency.model';

describe('CurrencyService', () => {
  let service: CurrencyService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [CurrencyService],
    });
    service = TestBed.inject(CurrencyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch supported currencies list', () => {
    const mockCurrencies: SupportedCurrency[] = [
      { code: 'USD', name: 'US Dollar', symbol: '$' },
      { code: 'EUR', name: 'Euro', symbol: '€' },
    ];

    service.getSupportedCurrencies().subscribe((data) => {
      expect(data.length).toBe(2);
      expect(data[0].code).toBe('USD');
    });

    const req = httpMock.expectOne('/api/currencies');
    expect(req.request.method).toBe('GET');
    req.flush(mockCurrencies);
  });

  it('should fetch exchange rate between two currencies', () => {
    const mockRate: CurrencyRate = {
      from: 'EUR',
      to: 'USD',
      rate: 1.085,
      date: '2026-10-30',
    };

    service.getRate('EUR', 'USD').subscribe((data) => {
      expect(data.rate).toBe(1.085);
      expect(data.from).toBe('EUR');
      expect(data.to).toBe('USD');
    });

    const req = httpMock.expectOne('/api/currencies/rate?from=EUR&to=USD');
    expect(req.request.method).toBe('GET');
    req.flush(mockRate);
  });
});
