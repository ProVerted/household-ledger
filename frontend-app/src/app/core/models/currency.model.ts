export interface SupportedCurrency {
  code: string;
  name: string;
  symbol: string;
}

export interface CurrencyRate {
  from: string;
  to: string;
  rate: number;
  date: string;
}
