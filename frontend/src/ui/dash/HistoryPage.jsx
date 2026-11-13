import { useEffect, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { dashboardPathForRole, ROLE_LABELS } from "../reg/accountFactory.js";
import SiteHeader from "./SiteHeader.jsx";
import useSessionAccount from "./useSessionAccount.js";
import "./dashboard.css";

const HISTORY_INTRO = {
  customer: "Your past orders and charges.",
  groceries: "Past orders and charges from your grocery store.",
  courier: "The deliveries you carried and their recorded charges.",
  operator: "Past orders and charges across all grocery stores.",
};

function money(cents) {
  return new Intl.NumberFormat(undefined, { style: "currency", currency: "CAD" }).format(cents / 100);
}

function readableState(state) {
  return String(state || "unknown").replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function dateTime(value) {
  if (!value) return "Time unavailable";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Time unavailable" : date.toLocaleString();
}

export default function HistoryPage() {
  const navigate = useNavigate();
  const { role: routeRole } = useParams();
  const { account, loading: sessionLoading } = useSessionAccount();
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!account) return undefined;
    const controller = new AbortController();
    async function loadHistory() {
      try {
        const response = await fetch("/api/history", {
          credentials: "include",
          signal: controller.signal,
        });
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body.error || "Order history could not be loaded.");
        setEntries(Array.isArray(body.entries) ? body.entries : []);
        setError("");
      } catch (requestError) {
        if (requestError.name !== "AbortError") {
          setError(requestError.message || "Order history could not be loaded.");
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    loadHistory();
    return () => controller.abort();
  }, [account]);

  if (sessionLoading) {
    return <main className="dash-main" aria-live="polite">Loading your session…</main>;
  }
  if (!account) return <Navigate to="/login" replace />;
  if (routeRole !== account.role) return <Navigate to={`${dashboardPathForRole(account.role)}/history`} replace />;

  return (
    <div className="dashboard">
      <a className="skip-link" href="#main">Skip to content</a>
      <SiteHeader account={account} />
      <main id="main" className="dash-main history-main">
        <button className="back-link" type="button" onClick={() => navigate(dashboardPathForRole(account.role))}>
          <span aria-hidden="true">←</span> Back
        </button>
        <div className="dash-intro dash-section order-history-heading">
          <p className="section-kicker">{ROLE_LABELS[account.role]} history</p>
          <h1>Order history</h1>
          <p className="lede">{HISTORY_INTRO[account.role] || "Past orders and charges from the ledger."}</p>
        </div>

        {loading && <p aria-live="polite">Loading order history…</p>}
        {error && <p className="form-alert" role="alert">{error}</p>}
        {!loading && !error && entries.length === 0 && (
          <section className="order-history-empty" aria-live="polite">
            <h2>No order history yet</h2>
            <p>Orders recorded in the ledger will appear here.</p>
          </section>
        )}
        {!loading && !error && entries.length > 0 && (
          <ol className="order-history-list">
            {entries.map((entry) => (
              <li className="order-history-card" key={entry.entryId}>
                <div className="order-history-summary">
                  <div>
                    <p className="section-kicker">Order {entry.orderId}</p>
                    <h2>{entry.storeName || entry.storeId || "Grocery store"}</h2>
                    <p className="order-history-state">Final status: {readableState(entry.finalState)}</p>
                  </div>
                  <div className="order-history-charge">
                    <span>{entry.entryType === "REFUND" ? "Refund" : "Charge"}</span>
                    <strong>{money(entry.amountCents)}</strong>
                    <time dateTime={entry.recordedAt}>{dateTime(entry.recordedAt)}</time>
                  </div>
                </div>
                <details className="order-history-timeline">
                  <summary>Order timeline</summary>
                  {entry.timestamps?.length > 0 ? (
                    <ol>
                      {entry.timestamps.map((timestamp, index) => (
                        <li key={`${entry.entryId}-${index}`}>
                          <span>{readableState(timestamp.state)}</span>
                          <time dateTime={timestamp.occurredAt}>{dateTime(timestamp.occurredAt)}</time>
                        </li>
                      ))}
                    </ol>
                  ) : (
                    <p>No transition timestamps were recorded.</p>
                  )}
                </details>
              </li>
            ))}
          </ol>
        )}
      </main>
    </div>
  );
}
