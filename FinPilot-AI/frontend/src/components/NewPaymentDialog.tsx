import { type FormEvent, useRef, useState } from "react";
import { ApiError, createPayment, newIdempotencyKey } from "../api";
import { CURRENCIES, type Payment } from "../types";

interface Props {
  onClose: () => void;
  onCreated: (payment: Payment) => void;
}

const ACCOUNT = /^[A-Z0-9]{8,34}$/;

export function NewPaymentDialog({ onClose, onCreated }: Props) {
  // One key per dialog session: if a submit fails or times out, retrying sends the SAME key,
  // so the backend can never create a duplicate payment.
  const idempotencyKey = useRef(newIdempotencyKey());
  const [form, setForm] = useState({ amount: "", currency: "USD", debtorAccount: "", creditorAccount: "", creditorName: "", description: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const set = (key: keyof typeof form, value: string) => setForm((f) => ({ ...f, [key]: value }));

  function validate(): Record<string, string> {
    const e: Record<string, string> = {};
    const amount = form.amount.trim();
    if (!amount) e.amount = "Amount is required";
    else if (!/^\d{1,15}(\.\d{1,2})?$/.test(amount) || Number(amount) < 0.01) e.amount = "Enter an amount of at least 0.01 with up to 2 decimals";
    if (!ACCOUNT.test(form.debtorAccount)) e.debtorAccount = "8-34 upper-case letters or digits";
    if (!ACCOUNT.test(form.creditorAccount)) e.creditorAccount = "8-34 upper-case letters or digits";
    if (!form.creditorName.trim()) e.creditorName = "Creditor name is required";
    return e;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found = validate();
    setErrors(found);
    setSubmitError(null);
    if (Object.keys(found).length > 0) return;

    setSubmitting(true);
    try {
      const payment = await createPayment(
        {
          amount: Number(form.amount),
          currency: form.currency,
          debtorAccount: form.debtorAccount,
          creditorAccount: form.creditorAccount,
          creditorName: form.creditorName.trim(),
          description: form.description.trim() || undefined,
        },
        idempotencyKey.current,
      );
      onCreated(payment);
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors) setErrors(error.fieldErrors);
      setSubmitError(error instanceof Error ? error.message : "Unexpected error");
    } finally {
      setSubmitting(false);
    }
  }

  const field = (key: keyof typeof form, label: string, extra: { placeholder?: string; upper?: boolean } = {}) => (
    <label>
      {label}
      <input
        value={form[key]}
        placeholder={extra.placeholder}
        onChange={(e) => set(key, extra.upper ? e.target.value.toUpperCase() : e.target.value)}
        aria-invalid={errors[key] ? true : undefined}
      />
      {errors[key] && <span className="field-error" role="alert">{errors[key]}</span>}
    </label>
  );

  return (
    <div className="modal" role="dialog" aria-modal="true" aria-labelledby="new-payment-title">
      <form className="modal__body" onSubmit={submit} noValidate>
        <h2 id="new-payment-title">New payment</h2>
        {field("amount", "Amount", { placeholder: "1250.50" })}
        <label>
          Currency
          <select value={form.currency} onChange={(e) => set("currency", e.target.value)}>
            {CURRENCIES.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </label>
        {field("debtorAccount", "Debtor account", { placeholder: "SYN100000007919", upper: true })}
        {field("creditorAccount", "Creditor account", { placeholder: "SYN100000099999", upper: true })}
        {field("creditorName", "Creditor name")}
        {field("description", "Description")}
        {submitError && <p className="panel__message--error" role="alert">{submitError}</p>}
        <div className="modal__actions">
          <button type="button" className="button button--ghost" onClick={onClose} disabled={submitting}>Cancel</button>
          <button type="submit" className="button" disabled={submitting}>{submitting ? "Submitting…" : "Submit payment"}</button>
        </div>
      </form>
    </div>
  );
}
