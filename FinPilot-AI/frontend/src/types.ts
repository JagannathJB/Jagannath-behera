export type PaymentStatus = "CREATED" | "VALIDATED" | "PROCESSING" | "REQUIRES_REVIEW" | "COMPLETED" | "FAILED";

export const PAYMENT_STATUSES: PaymentStatus[] = ["CREATED", "VALIDATED", "PROCESSING", "REQUIRES_REVIEW", "COMPLETED", "FAILED"];
export const CURRENCIES = ["USD", "EUR", "GBP", "INR", "JPY"];

/** Mirrors the backend PaymentResponse DTO. Fields added in Milestone 3 are null on legacy rows. */
export interface Payment {
  id: string;
  paymentReference: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  debtorAccount: string | null;
  creditorAccount: string | null;
  creditorName: string | null;
  description: string | null;
  failureReason: string | null;
  correlationId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface StatusHistoryEntry {
  fromStatus: PaymentStatus | null;
  toStatus: PaymentStatus;
  reason: string | null;
  correlationId: string | null;
  changedAt: string;
}

export interface PaymentDetail {
  payment: Payment;
  history: StatusHistoryEntry[];
  allowedTransitions: PaymentStatus[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PaymentFilters {
  status: PaymentStatus | "";
  currency: string;
  minAmount: string;
  maxAmount: string;
  reference: string;
}

export const EMPTY_FILTERS: PaymentFilters = { status: "", currency: "", minAmount: "", maxAmount: "", reference: "" };

export interface CreatePaymentInput {
  amount: number;
  currency: string;
  debtorAccount: string;
  creditorAccount: string;
  creditorName: string;
  description?: string;
}

export interface HealthResponse {
  status: string;
}
