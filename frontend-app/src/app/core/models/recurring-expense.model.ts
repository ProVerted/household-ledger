export interface CreateRecurringExpenseRequest {
  description: string;
  amount: number;
  currency: string;
  category: string | null;
  dayOfMonth: number;
  paidByUserId: number | null;
}

export interface RecurringExpenseResponse {
  id: number;
  description: string;
  amount: number;
  currency: string;
  category: string | null;
  dayOfMonth: number;
  active: boolean;
  paidByUserId: number;
  paidByName: string;
  lastGeneratedAt: string | null;
}