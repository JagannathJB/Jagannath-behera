import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

type Call = { url: string; method: string; init?: RequestInit };
type Handler = (url: string, init?: RequestInit) => Promise<Response> | undefined;

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status }));
const page = (content: unknown[], totalElements = content.length, totalPages = content.length ? 1 : 0) => ({
  content, page: 0, size: 10, totalElements, totalPages,
});
const payment = (over: Record<string, unknown> = {}) => ({
  id: "1", paymentReference: "PAY-00001000", amount: 1250.5, currency: "USD", status: "COMPLETED",
  debtorAccount: "SYN100000007919", creditorAccount: "SYN100000099999", creditorName: "Northwind Traders",
  description: null, failureReason: null, correlationId: "c", createdAt: "2026-01-15T10:15:30Z", updatedAt: "2026-01-15T10:15:31Z",
  ...over,
});

let calls: Call[] = [];
function stub(handler: Handler) {
  calls = [];
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string, init?: RequestInit) => {
      calls.push({ url, method: init?.method ?? "GET", init });
      const handled = handler(url, init);
      if (handled) return handled;
      if (url.startsWith("/actuator/health")) return json({ status: "UP" });
      return Promise.reject(new Error(`unexpected request ${init?.method ?? "GET"} ${url}`));
    }),
  );
}
const listCalls = () => calls.filter((c) => c.method === "GET" && c.url.startsWith("/api/v1/payments?"));

beforeEach(() => {
  calls = [];
});

describe("FinPilot dashboard", () => {
  it("shows a loading state, then the empty state, and a healthy backend", async () => {
    stub((url) => (url.startsWith("/api/v1/payments?") ? json(page([])) : undefined));
    render(<App />);

    expect(screen.getByText("Loading payments…")).toBeInTheDocument();
    expect(await screen.findByText("No payments yet")).toBeInTheDocument();
    expect(await screen.findByText("Backend UP")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("renders payments with pagination and requests the next page", async () => {
    stub((url) => (url.startsWith("/api/v1/payments?") ? json(page([payment()], 25, 3)) : undefined));
    render(<App />);

    expect(await screen.findByRole("table")).toBeInTheDocument();
    expect(screen.getByText("PAY-00001000")).toBeInTheDocument();
    expect(screen.getByText("$1,250.50")).toBeInTheDocument();
    expect(screen.getByText(/25 payments · Page 1 of 3/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();

    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(listCalls().some((c) => c.url.includes("page=1"))).toBe(true));
  });

  it("sends filters to the API and shows a no-match state", async () => {
    stub((url) => (url.startsWith("/api/v1/payments?") ? json(page([])) : undefined));
    render(<App />);
    await screen.findByText("No payments yet");

    await userEvent.selectOptions(screen.getByLabelText("Status"), "FAILED");
    await userEvent.type(screen.getByLabelText("Min amount"), "100");
    await userEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(await screen.findByText("No payments match your filters")).toBeInTheDocument();
    const last = listCalls().at(-1)!;
    expect(last.url).toContain("status=FAILED");
    expect(last.url).toContain("minAmount=100");
    expect(last.url).toContain("page=0");
  });

  it("shows an error with retry when the API fails, and recovers on retry", async () => {
    let attempts = 0;
    stub((url) => (url.startsWith("/api/v1/payments?") ? (++attempts === 1 ? json({ detail: "Database is down" }, 500) : json(page([]))) : undefined));
    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Database is down");
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByText("No payments yet")).toBeInTheDocument();
  });

  it("reports an unreachable backend in the health indicator and payments panel", async () => {
    stub((url) => (url.startsWith("/api/v1/payments?") || url.startsWith("/actuator") ? Promise.reject(new TypeError("network down")) : undefined));
    render(<App />);

    expect(await screen.findByText("Backend unreachable")).toBeInTheDocument();
    expect(await screen.findByRole("alert")).toHaveTextContent("Cannot reach the FinPilot backend");
  });

  it("shows DOWN when the backend reports a non-UP status", async () => {
    stub((url) => {
      if (url.startsWith("/api/v1/payments?")) return json(page([]));
      if (url.startsWith("/actuator/health")) return json({ status: "DOWN" }, 503);
    });
    render(<App />);

    expect(await screen.findByText("Backend DOWN")).toBeInTheDocument();
  });

  it("validates the new-payment form client-side before sending anything", async () => {
    stub((url) => (url.startsWith("/api/v1/payments?") ? json(page([])) : undefined));
    render(<App />);
    await screen.findByText("No payments yet");

    await userEvent.click(screen.getByRole("button", { name: "New payment" }));
    await userEvent.click(screen.getByRole("button", { name: "Submit payment" }));

    expect(await screen.findByText("Amount is required")).toBeInTheDocument();
    expect(screen.getAllByText("8-34 upper-case letters or digits")).toHaveLength(2);
    expect(screen.getByText("Creditor name is required")).toBeInTheDocument();
    expect(calls.some((c) => c.method === "POST")).toBe(false);
  });

  it("creates a payment with an idempotency key and refreshes the list", async () => {
    stub((url, init) => {
      if (init?.method === "POST" && url === "/api/v1/payments") return json(payment({ status: "VALIDATED" }), 201);
      if (url.startsWith("/api/v1/payments?")) return json(page([]));
    });
    render(<App />);
    await screen.findByText("No payments yet");

    await userEvent.click(screen.getByRole("button", { name: "New payment" }));
    await userEvent.type(screen.getByLabelText(/^Amount/), "1250.50");
    await userEvent.type(screen.getByLabelText(/^Debtor account/), "syn100000007919");
    await userEvent.type(screen.getByLabelText(/^Creditor account/), "syn100000099999");
    await userEvent.type(screen.getByLabelText(/^Creditor name/), "Northwind Traders");
    await userEvent.click(screen.getByRole("button", { name: "Submit payment" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    const post = calls.find((c) => c.method === "POST")!;
    const headers = post.init?.headers as Record<string, string>;
    expect(headers["Idempotency-Key"]).toMatch(/\S{8,}/);
    expect(JSON.parse(post.init?.body as string)).toMatchObject({
      amount: 1250.5, currency: "USD", debtorAccount: "SYN100000007919", creditorAccount: "SYN100000099999",
    });
    await waitFor(() => expect(listCalls().length).toBeGreaterThanOrEqual(2));
  });

  it("reuses the same idempotency key when a failed submission is retried", async () => {
    let attempts = 0;
    stub((url, init) => {
      if (init?.method === "POST" && url === "/api/v1/payments") return ++attempts === 1 ? json({ detail: "boom" }, 500) : json(payment(), 201);
      if (url.startsWith("/api/v1/payments?")) return json(page([]));
    });
    render(<App />);
    await screen.findByText("No payments yet");

    await userEvent.click(screen.getByRole("button", { name: "New payment" }));
    await userEvent.type(screen.getByLabelText(/^Amount/), "10");
    await userEvent.type(screen.getByLabelText(/^Debtor account/), "SYN100000007919");
    await userEvent.type(screen.getByLabelText(/^Creditor account/), "SYN100000099999");
    await userEvent.type(screen.getByLabelText(/^Creditor name/), "Acme");
    await userEvent.click(screen.getByRole("button", { name: "Submit payment" }));
    expect(await screen.findByText("boom")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Submit payment" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

    const keys = calls.filter((c) => c.method === "POST").map((c) => (c.init?.headers as Record<string, string>)["Idempotency-Key"]);
    expect(keys).toHaveLength(2);
    expect(keys[0]).toBe(keys[1]);
  });

  it("shows payment history and drives a state transition, requiring a reason for FAILED", async () => {
    const detail = {
      payment: payment({ status: "VALIDATED" }),
      history: [
        { fromStatus: null, toStatus: "CREATED", reason: "Payment created", correlationId: "c", changedAt: "2026-01-15T10:15:30Z" },
        { fromStatus: "CREATED", toStatus: "VALIDATED", reason: "Validation passed", correlationId: "c", changedAt: "2026-01-15T10:15:31Z" },
      ],
      allowedTransitions: ["PROCESSING", "REQUIRES_REVIEW", "FAILED"],
    };
    stub((url, init) => {
      if (init?.method === "POST" && url === "/api/v1/payments/1/transitions") return json(detail);
      if (url === "/api/v1/payments/1") return json(detail);
      if (url.startsWith("/api/v1/payments?")) return json(page([payment({ status: "VALIDATED" })]));
    });
    render(<App />);

    await userEvent.click(await screen.findByRole("button", { name: "View PAY-00001000" }));
    expect(await screen.findByText("Validation passed")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "FAILED" }));
    expect(await screen.findByText(/A reason is required to move a payment to FAILED/)).toBeInTheDocument();
    expect(calls.some((c) => c.method === "POST")).toBe(false);

    await userEvent.click(screen.getByRole("button", { name: "PROCESSING" }));
    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
    const post = calls.find((c) => c.method === "POST")!;
    expect(post.url).toBe("/api/v1/payments/1/transitions");
    expect(JSON.parse(post.init?.body as string)).toMatchObject({ targetStatus: "PROCESSING" });
  });
});
