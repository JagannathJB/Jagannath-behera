import { HealthIndicator } from "./components/HealthIndicator";
import { PaymentsPanel } from "./components/PaymentsPanel";

const NAV = [
  { label: "Payments", active: true },
  { label: "Risk alerts", active: false },
  { label: "Investigations", active: false },
  { label: "AI copilot", active: false },
];

export default function App() {
  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand__mark" aria-hidden="true">F</span>
          FinPilot AI
        </div>
        <HealthIndicator />
      </header>

      <nav className="sidebar" aria-label="Primary">
        <ul>
          {NAV.map((item) => (
            <li key={item.label}>
              {item.active ? (
                <span className="nav__item nav__item--active" aria-current="page">{item.label}</span>
              ) : (
                <span className="nav__item nav__item--disabled" aria-disabled="true">
                  {item.label} <small>soon</small>
                </span>
              )}
            </li>
          ))}
        </ul>
      </nav>

      <main className="content">
        <h1>Payments</h1>
        <p className="subtitle">Payment operations overview. Synthetic data only.</p>
        <PaymentsPanel />
      </main>
    </div>
  );
}
