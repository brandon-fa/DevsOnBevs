import { useCallback, useEffect, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { stores } from "../../data/mockData.js";
import { ROLE_LABELS, dashboardPathForRole } from "../reg/accountFactory.js";
import { clearAccount, saveAccount } from "../reg/accountSession.js";
import StoreOrders from "../ord/StoreOrders.jsx";
import StoreList from "../ord/StoreList.jsx";
import CustomerOrders from "../ord/CustomerOrders.jsx";
import CityMap from "./CityMap.jsx";
import ConsolePanel from "./ConsolePanel.jsx";
import RoleDashboardView from "./RoleDashboardView.jsx";
import "./dashboard.css";

export default function Dashboard() {
  const navigate = useNavigate();
  const { role: routeRole } = useParams();
  const [account, setAccount] = useState(null);
  const [loading, setLoading] = useState(true);
  const [consoleMessages, setConsoleMessages] = useState([]);
  const [latestOrder, setLatestOrder] = useState(null);
  const [trackedOrder, setTrackedOrder] = useState(null);

  // Console updates must not restart the child components' API polling.
  const addFeedback = useCallback((text, type = "info") => {
    const timestamp = new Date().toISOString();
    setConsoleMessages((previous) => {
      const entry = { id: previous.length + 1, text: text, type: type, timestamp: timestamp };
      return previous.concat(entry);
    });
  }, []);

  useEffect(() => {
    document.title = "Dashboard · Little Isles";
    let active = true;

    async function loadAccount() {
      try {
        const response = await fetch("/api/session", { credentials: "include" });
        if (response.ok) {
          const currentAccount = await response.json();
          if (active) {
            saveAccount(currentAccount);
            setAccount(currentAccount);
          }
        }
      } catch {
        // An unavailable or expired session returns the user to login.
      } finally {
        if (active) setLoading(false);
      }
    }
    loadAccount();
    return () => { active = false; };
  }, []);

  if (loading) {
    return <main className="dash-main" aria-live="polite">Loading your dashboard…</main>;
  }
  if (!account) {
    return <Navigate to="/login" replace />;
  }
  if (routeRole !== account.role) {
    return <Navigate to={dashboardPathForRole(account.role)} replace />;
  }

  async function signOut() {
    try {
      await fetch("/api/logout", { method: "POST", credentials: "include" });
    } catch {
      // Still clear the local session when the server cannot be reached.
    } finally {
      clearAccount();
      navigate("/login");
    }
  }

  let view;
  if (account.role === "customer") {
    view = <CustomerView latestOrder={latestOrder} onSelectOrder={setTrackedOrder}
      onOrderPlaced={setLatestOrder} onFeedback={addFeedback} />;
  } else if (account.role === "groceries") {
    view = <StoreOrders onFeedback={addFeedback} />;
  } else {
    view = <RoleDashboardView role={account.role} />;
  }

  return (
    <div className="dashboard">
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="site-header">
        <div className="site-header-inner">
          <p className="wordmark">Little Isles</p>
          <div className="account-cluster">
            <p className="account-name">{account.fullName}</p>
            <p className="role-badge">{ROLE_LABELS[account.role]}</p>
            <button className="sign-out" type="button" onClick={signOut}>Sign out</button>
          </div>
        </div>
      </header>
      <main id="main" className="dash-main">
        <CityMap role={account.role} account={account} order={trackedOrder} onFeedback={addFeedback} />
        {view}
        <ConsolePanel messages={consoleMessages} />
      </main>
    </div>
  );
}

function CustomerView({ latestOrder, onSelectOrder, onOrderPlaced, onFeedback }) {
  return (
    <>
      <div className="dash-intro dash-section">
        <h1>Your market</h1>
        <p className="lede">Browse nearby groceries and follow your orders as the store prepares them.</p>
      </div>
      <CustomerOrders latestOrder={latestOrder} onSelectOrder={onSelectOrder} onFeedback={onFeedback} />
      <section className="dash-section" aria-labelledby="nearby-title">
        <div className="section-heading">
          <h2 id="nearby-title">Nearby groceries</h2>
          <p>Markets stocking the foods people cook at home.</p>
        </div>
        <ul className="store-grid">
          {stores.map((store) => (
            <li key={store.id} className="store-card">
              <div className="store-card-inner">
                <p className="store-mark" aria-hidden="true">{store.mark}</p>
                <h3>{store.name}</h3>
                <p className="store-place">{store.cuisine} · {store.neighborhood}</p>
                <p className="store-goods">{store.goods}</p>
              </div>
            </li>
          ))}
        </ul>
      </section>
      <StoreList onFeedback={onFeedback} onOrderPlaced={onOrderPlaced} />
    </>
  );
}
