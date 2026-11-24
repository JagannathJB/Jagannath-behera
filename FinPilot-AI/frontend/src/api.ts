import type {
  CreatePaymentInput,
  HealthResponse,
  PageResponse,
  Payment,
  PaymentDetail,
  PaymentFilters,
  PaymentStatus,
} from "./types";

export class ApiError extends Error {
  readonly status?: number;
  readonly fieldErrors?: Record<string, string>;

  constructor(message: string, status?: number, fieldErrors?: Record<string, string>) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  const body = (await response.json().catch(() => null)) as { detail?: string; errors?: Record<string, string> } | null;
  const detail = body?.detail ?? `Request failed (HTTP ${response.status}). Check that the backend and database are running.`;
  return new ApiError(detail, response.status, body?.errors);
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, { ...init, headers: { Accept: "application/json", ...init.headers } });
  } catch (error) {
    if (init.signal?.aborted) throw error;
    throw new ApiError("Cannot reach the FinPilot backend. Is it running on port 8080?");
  }
  if (!response.ok) throw await toApiError(response);
  return (await response.json()) as T;
}

export function searchPayments(filters: PaymentFilters, page: number, size: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort: "createdAt,desc" });
  if (filters.status) params.set("status", filters.status);
  if (filters.currency.trim()) params.set("currency", filters.currency.trim());
  if (filters.minAmount.trim()) params.set("minAmount", filters.minAmount.trim());
  if (filters.maxAmount.trim()) params.set("maxAmount", filters.maxAmount.trim());
  if (filters.reference.trim()) params.set("reference", filters.reference.trim());
  return request<PageResponse<Payment>>(`/api/v1/payments?${params.toString()}`, { signal });
}

export const fetchPaymentDetail = (id: string, signal?: AbortSignal) =>
  request<PaymentDetail>(`/api/v1/payments/${id}`, { signal });

/** The idempotency key must be generated once per user intent and reused on retries of the same submission. */
export function createPayment(input: CreatePaymentInput, idempotencyKey: string) {
  return request<Payment>("/api/v1/payments", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(input),
  });
}

export function transitionPayment(id: string, targetStatus: PaymentStatus, reason: string) {
  return request<PaymentDetail>(`/api/v1/payments/${id}/transitions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ targetStatus, reason: reason.trim() || undefined }),
  });
}

/** Actuator answers HTTP 503 with {"status":"DOWN"} when a dependency (e.g. the database) is failing, so read the body. */
export async function fetchHealth(signal?: AbortSignal): Promise<HealthResponse> {
  let response: Response;
  try {
    response = await fetch("/actuator/health", { headers: { Accept: "application/json" }, signal });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new ApiError("Cannot reach the FinPilot backend. Is it running on port 8080?");
  }
  const body = (await response.json().catch(() => null)) as { status?: unknown } | null;
  if (typeof body?.status === "string") return { status: body.status };
  throw new ApiError(`Health check failed (HTTP ${response.status}).`);
}

export function newIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  return `key-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}
