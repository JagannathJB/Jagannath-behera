import { useCallback, useState } from "react";
import { searchPayments } from "../api";
import { useApi } from "../useApi";
import { EMPTY_FILTERS, type PaymentFilters } from "../types";
import { NewPaymentDialog } from "./NewPaymentDialog";
import { PaymentDetailPanel } from "./PaymentDetailPanel";
import { PaymentFilterBar } from "./PaymentFilterBar";
import { StatusBadge } from "./StatusBadge";

const PAGE_SIZE = 10;

function formatAmount(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
  } catch {
    return `${amount} ${currency}`;
  }
}

export function PaymentsPanel() {
  const [filters, setFilters] = useState<PaymentFilters>(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showNew, setShowNew] = useState(false);

  const fetcher = useCallback((signal: AbortSignal) => searchPayments(filters, page, PAGE_SIZE, signal), [filters, page]);
  const { state, reload } = useApi(fetcher);
  const filtersActive = JSON.stringify(filters) !== JSON.stringify(EMPTY_FILTERS);

  const applyFilters = (next: PaymentFilters) => {
    setPage(0);
    setFilters(next);
  };

  let body;
  if (state.status === "loading") {
    body = <div className="panel__message" role="status">Loading payments…</div>;
  } else if (state.status === "error") {
    body = (
      <div className="panel__message panel__message--error" role="alert">
        <strong>Could not load payments.</strong>
        <p>{state.message}</p>
        <button type="button" className="button" onClick={reload}>Retry</button>
      </div>
    );
  } else if (state.data.content.length === 0) {
    body = filtersActive ? (
      <div className="panel__message">
        <strong>No payments match your filters</strong>
        <p>Try widening the amount range or clearing a filter.</p>
      </div>
    ) : (
      <div className="panel__message">
        <strong>No payments yet</strong>
        <p>Create a payment, or enable SEED_ENABLED=true in .env and restart the backend to load synthetic demo data.</p>
      </div>
    );
  } else {
    const { content, totalElements, totalPages } = state.data;
    body = (
      <>
        <div className="table-wrap">
          <table>
            <caption className="sr-only">Payments, newest first</caption>
            <thead>
              <tr>
                <th scope="col">Reference</th>
                <th scope="col" className="num">Amount</th>
                <th scope="col">Status</th>
                <th scope="col">Created</th>
                <th scope="col"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {content.map((p) => (
                <tr key={p.id}>
                  <td>{p.paymentReference}</td>
                  <td className="num">{formatAmount(p.amount, p.currency)}</td>
                  <td><StatusBadge status={p.status} /></td>
                  <td>{new Date(p.createdAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}</td>
                  <td>
                    <button type="button" className="button button--ghost" aria-label={`View ${p.paymentReference}`} onClick={() => setSelectedId(p.id)}>
                      View
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="pager">
          <span>{totalElements} payments · Page {page + 1} of {Math.max(totalPages, 1)}</span>
          <div>
            <button type="button" className="button button--ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</button>
            <button type="button" className="button button--ghost" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>Next</button>
          </div>
        </div>
      </>
    );
  }

  return (
    <section className="panel" aria-labelledby="payments-heading">
      <div className="panel__header">
        <h2 id="payments-heading" className="panel__title">Payments</h2>
        <button type="button" className="button" onClick={() => setShowNew(true)}>New payment</button>
      </div>
      <PaymentFilterBar onApply={applyFilters} />
      {body}
      {showNew && (
        <NewPaymentDialog
          onClose={() => setShowNew(false)}
          onCreated={() => {
            setShowNew(false);
            setPage(0);
            reload();
          }}
        />
      )}
      {selectedId && <PaymentDetailPanel paymentId={selectedId} onClose={() => setSelectedId(null)} onChanged={reload} />}
    </section>
  );
}
