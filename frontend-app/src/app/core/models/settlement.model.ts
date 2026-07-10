export interface BalanceResponse {
    userId: number;
    name: string;
    netBalance: number;
}

export interface SettlementSuggestionResponse {
    fromUserId: number;
    fromName: string;
    toUserId: number;
    toName: string;
    amount: number;
}

export interface ConfirmSettlementRequest {
    fromUserId: number;
    toUserId: number;
    amount: number;
}

export interface SettlementTransactionResponse {
    id: number;
    fromUserId: number;
    fromName: string;
    toUserId: number;
    toName: string;
    amount: number;
    settledAt: string;
}