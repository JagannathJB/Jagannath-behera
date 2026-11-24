import { type FormEvent, useState } from "react";
import { CURRENCIES, EMPTY_FILTERS, PAYMENT_STATUSES, type PaymentFilters, type PaymentStatus } from "../types";

interface Props {
  onApply: (filters: PaymentFilters) => void;
}

export function PaymentFilterBar({ onApply }: Props) {
  const [draft, setDraft] = useState<PaymentFilters>(EMPTY_FILTERS);
  const set = <K extends keyof PaymentFilters>(key: K, value: PaymentFilters[K]) => setDraft((d) => ({ ...d, [key]: value }));

  const submit = (event: FormEvent) => {
    event.preventDefault();
    onApply(draft);
  };
  const reset = () => {
    setDraft(EMPTY_FILTERS);
    onApply(EMPTY_FILTERS);
  };

  return (
    <form className="filters" onSubmit={submit} aria-label="Payment filters">
      <label>
        Reference
        <input value={draft.reference} onChange={(e) => set("reference", e.target.value)} placeholder="PAY-0000" />
      </label>
      <label>
        Status
        <select value={draft.status} onChange={(e) => set("status", e.target.value as PaymentStatus | "")}>
          <option value="">All</option>
          {PAYMENT_STATUSES.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </label>
      <label>
        Currency
        <select value={draft.currency} onChange={(e) => set("currency", e.target.value)}>
          <option value="">All</option>
          {CURRENCIES.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
      </label>
      <label>
        Min amount
        <input inputMode="decimal" value={draft.minAmount} onChange={(e) => set("minAmount", e.target.value)} />
      </label>
      <label>
        Max amount
        <input inputMode="decimal" value={draft.maxAmount} onChange={(e) => set("maxAmount", e.target.value)} />
      </label>
      <div className="filters__actions">
        <button type="submit" className="button">Apply</button>
        <button type="button" className="button button--ghost" onClick={reset}>Reset</button>
      </div>
    </form>
  );
}
