import type { PaymentStatus } from "../types";

export function StatusBadge({ status }: { status: PaymentStatus }) {
  return <span className={`badge badge--${status.toLowerCase()}`}>{status.replace("_", " ")}</span>;
}
