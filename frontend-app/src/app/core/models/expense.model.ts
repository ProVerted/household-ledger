export type SplitType = 'EQUAL' | 'PERCENTAGE' | 'EXACT';
export type ExpenseStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';

export interface SplitInput {
  userId: number;
  value: number | null;
}

export interface CreateExpenseRequest {
  description: string;
  amount: number;
  currency: string;
  exchangeRate?: number | null;
  category: string | null;
  expenseDate: string;
  paidByUserId: number | null;
  splitType: SplitType;
  splits: SplitInput[];
}

export interface UpdateExpenseRequest extends CreateExpenseRequest {
  version: number;
}

export interface ExpenseSplitResponse {
  userId: number;
  name: string;
  shareAmount: number;
  baseCurrencyShareAmount?: number;
  sharePercentage: number | null;
}

export interface ExpenseResponse {
  id: number;
  description: string;
  amount: number;
  currency: string;
  exchangeRate?: number;
  baseCurrencyAmount?: number;
  baseCurrency?: string;
  category: string | null;
  expenseDate: string;
  paidByUserId: number;
  paidByName: string;
  splitType: SplitType;
  status: ExpenseStatus;
  version: number;
  splits: ExpenseSplitResponse[];
}
