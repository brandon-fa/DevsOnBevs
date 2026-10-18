import { useEffect, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { ORDER_STEPS, activeOrder, formatMoney, stores } from "../../data/mockData.js";
import { ROLE_LABELS } from "../reg/accountFactory.js";
import { clearAccount, saveAccount } from "../reg/accountSession.js";
import { dashboardPathForRole } from "../reg/accountFactory.js";
import CityMap from "./CityMap.jsx";
import RoleDashboardView from "./RoleDashboardView.jsx";
import "./dashboard.css";

export default function Dashboard() {
  const navigate = useNavigate();
  const { role: routeRole } = useParams();
  const [account, setAccount] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    document.title = "Dashboard · Little Isles";
    fetch("/api/session", { credentials: "include" })
      .then(async (response) => {
        if (!response.ok) return null;
        return response.json();
      })
      .then((currentAccount) => {
        if (currentAccount) {
          saveAccount(currentAccount);
          setAccount(currentAccount);
        }
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <main className="dash-main" aria-live="polite">Loading your dashboard…</main>;
  if (!account) {
    return <Navigate to="/login" replace />;
  }
  if (routeRole !== account.role) {
    return <Navigate to={dashboardPathForRole(account.role)} replace />;
  }

  const currentStep = ORDER_STEPS.findIndex(([key]) => key === activeOrder.status);

  async function signOut() {
    await fetch("/api/logout", { method: "POST", credentials: "include" }).catch(() => {});
    clearAccount();
    navigate("/login");
  }

  return (
    <div className="dashboard">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="site-header">
        <div className="site-header-inner">
          <p className="wordmark">Little Isles</p>
          <div className="account-cluster">
            <p className="account-name">{account.fullName}</p>
            <p className="role-badge">{ROLE_LABELS[account.role]}</p>
            <button className="sign-out" type="button" onClick={signOut}>
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main id="main" className="dash-main">
        <CityMap />
        {account.role === "customer" ? (
          <>
        <div className="dash-intro dash-section">
          <h1>Your market</h1>
          <p className="lede">
            Ethnic groceries nearby, and the order a courier is bringing to the door.
          </p>
        </div>

        <section className="order-card dash-section" aria-labelledby="active-order-title">
          <div>
            <p className="section-kicker">Active order</p>
            <h2 id="active-order-title">On the way from {activeOrder.store}</h2>
            <p className="order-copy">
              {activeOrder.courier} is bringing order{" "}
              <span className="order-id">{activeOrder.id}</span> to {activeOrder.address}, arriving
              around {activeOrder.eta}
            </p>
            <ol className="steps" aria-label="Delivery progress">
              {ORDER_STEPS.map(([key, label], index) => {
                const state = index < currentStep ? "is-done" : index === currentStep ? "is-current" : "";
                const stateLabel = index < currentStep ? "Done" : index === currentStep ? "Now" : "Later";
                return (
                  <li
                    key={key}
                    className={`step ${state}`.trim()}
                    aria-current={index === currentStep ? "step" : undefined}
                  >
                    <span className="step-label">{label}</span>
                    <span className="step-state">{stateLabel}</span>
                  </li>
                );
              })}
            </ol>
          </div>
          <div className="order-meta">
            <ul className="item-list">
              {activeOrder.items.map((item) => (
                <li key={item.name}>
                  <span>
                    {item.quantity} × {item.name}
                  </span>
                  <span className="money">{formatMoney(item.priceCents * item.quantity)}</span>
                </li>
              ))}
            </ul>
            <p className="fee-row">
              <span>Delivery</span>
              <span className="money">{formatMoney(activeOrder.deliveryFeeCents)}</span>
            </p>
            <p className="total-row">
              <span>Total</span>
              <span className="money">{formatMoney(activeOrder.totalCents)}</span>
            </p>
          </div>
        </section>

        <section className="dash-section" aria-labelledby="nearby-title">
          <div className="section-heading">
            <h2 id="nearby-title">Nearby groceries</h2>
            <p>Markets stocking the foods people cook at home.</p>
          </div>
          <ul className="store-grid">
            {stores.map((store) => (
              <li key={store.id} className="store-card">
                <div className="store-card-inner">
                  <p className="store-mark" aria-hidden="true">
                    {store.mark}
                  </p>
                  <h3>{store.name}</h3>
                  <p className="store-place">
                    {store.cuisine} · {store.neighborhood}
                  </p>
                  <p className="store-goods">{store.goods}</p>
                </div>
              </li>
            ))}
          </ul>
        </section>
          </>
        ) : (
          <RoleDashboardView role={account.role} />
        )}
      </main>
    </div>
  );
}
