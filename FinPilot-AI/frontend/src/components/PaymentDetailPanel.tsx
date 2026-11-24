import { useCallback, useState } from "react";
import { fetchPaymentDetail, transitionPayment } from "../api";
import { useApi } from "../useApi";
import type { PaymentStatus } from "../types";
import { StatusBadge } from "./StatusBadge";

interface Props {
  paymentId: string;
  onClose: () => void;
  onChanged: () => void;
}

const NEEDS_REASON: PaymentStatus[] = ["FAILED", "REQUIRES_REVIEW"];

export function PaymentDetailPanel({ paymentId, onClose, onChanged }: Props) {
  const fetcher = useCallback((signal: AbortSignal) => fetchPaymentDetail(paymentId, signal), [paymentId]);
  const { state, reload } = useApi(fetcher);
  const [reason, setReason] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function move(target: PaymentStatus) {
    setActionError(null);
    if (NEEDS_REASON.includes(target) && !reason.trim()) {
      setActionError(`A reason is required to move a payment to ${target}`);
      return;
    }
    setBusy(true);
    try {
      await transitionPayment(paymentId, target, reason);
      setReason("");
      reload();
      onChanged();
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "Unexpected error");
    } finally {
      setBusy(false);
    }
  }

  return (
    <aside className="drawer" aria-label="Payment details">
      <div className="drawer__header">
        <h2>Payment details</h2>
        <button type="button" className="button button--ghost" onClick={onClose}>Close</button>
      </div>

      {state.status === "loading" && <p role="status">Loading payment…</p>}
      {state.status === "error" && (
        <div role="alert" className="panel__message--error">
          <p>{state.message}</p>
          <button type="button" className="button" onClick={reload}>Retry</button>
        </div>
      )}
      {state.status === "success" && (
        <>
          <dl className="details">
            <dt>Reference</dt><dd>{state.data.payment.paymentReference}</dd>
            <dt>Status</dt><dd><StatusBadge status={state.data.payment.status} /></dd>
            <dt>Amount</dt><dd>{state.data.payment.amount} {state.data.payment.currency}</dd>
            <dt>Debtor</dt><dd>{state.data.payment.debtorAccount ?? "—"}</dd>
            <dt>Creditor</dt><dd>{state.data.payment.creditorName ?? "—"} ({state.data.payment.creditorAccount ?? "—"})</dd>
            {state.data.payment.failureReason && (<><dt>Failure reason</dt><dd>{state.data.payment.failureReason}</dd></>)}
          </dl>

          <h3>Status history</h3>
          {state.data.history.length === 0 ? (
            <p className="muted">No history recorded for this payment.</p>
          ) : (
            <ol className="timeline">
              {state.data.history.map((h, i) => (
                <li key={i}>
                  <StatusBadge status={h.toStatus} /> <span className="muted">{new Date(h.changedAt).toLocaleString()}</span>
                  {h.reason && <div>{h.reason}</div>}
                </li>
              ))}
            </ol>
          )}

          {state.data.allowedTransitions.length > 0 ? (
            <>
              <h3>Simulate processing</h3>
              <label>
                Reason (required for FAILED / REQUIRES REVIEW)
                <input value={reason} onChange={(e) => setReason(e.target.value)} />
              </label>
              <div className="actions">
                {state.data.allowedTransitions.map((target) => (
                  <button key={target} type="button" className="button button--ghost" disabled={busy} onClick={() => void move(target)}>
                    {target}
                  </button>
                ))}
              </div>
            </>
          ) : (
            <p className="muted">This payment is in a final state.</p>
          )}
          {actionError && <p className="panel__message--error" role="alert">{actionError}</p>}
        </>
      )}
    </aside>
  );
}
