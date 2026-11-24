import { fetchHealth } from "../api";
import { useApi } from "../useApi";

const REFRESH_MS = 15_000;

export function HealthIndicator() {
  const { state } = useApi(fetchHealth, REFRESH_MS);

  let tone = "pending";
  let label = "Checking backend…";
  if (state.status === "error") {
    tone = "down";
    label = "Backend unreachable";
  } else if (state.status === "success") {
    const up = state.data.status === "UP";
    tone = up ? "up" : "down";
    label = up ? "Backend UP" : `Backend ${state.data.status}`;
  }

  return (
    <div className={`health health--${tone}`} role="status" aria-live="polite">
      <span className="health__dot" aria-hidden="true" />
      {label}
    </div>
  );
}
