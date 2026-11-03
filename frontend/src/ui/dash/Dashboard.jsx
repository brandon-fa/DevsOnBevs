import { useCallback, useEffect, useState } from "react";
import { Navigate, useParams } from "react-router-dom";
import { dashboardPathForRole } from "../reg/accountFactory.js";
import StoreOrders from "../ord/StoreOrders.jsx";
import StoreList from "../ord/StoreList.jsx";
import CustomerOrders from "../ord/CustomerOrders.jsx";
import CityMap from "./CityMap.jsx";
import RoleDashboardView from "./RoleDashboardView.jsx";
import SiteHeader from "./SiteHeader.jsx";
import { appendConsoleMessage } from "./consoleFeed.js";
import useSessionAccount from "./useSessionAccount.js";
import "./dashboard.css";

export default function Dashboard() {
  const { role: routeRole } = useParams();
  const { account, loading } = useSessionAccount();
  const [trackedOrder, setTrackedOrder] = useState(null);

  // Console updates must not restart the child components' API polling.
  const addFeedback = useCallback((text, type = "info") => {
    appendConsoleMessage(text, type);
  }, []);

  useEffect(() => {
    document.title = "Dashboard · Little Isles";
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

  let view;
  if (account.role === "customer") {
    view = <CustomerView onSelectOrder={setTrackedOrder} onFeedback={addFeedback} />;
  } else if (account.role === "groceries") {
    view = <StoreOrders onFeedback={addFeedback} />;
  } else {
    view = <RoleDashboardView role={account.role} />;
  }

  return (
    <div className="dashboard">
      <a className="skip-link" href="#main">Skip to content</a>
      <SiteHeader account={account} />
      <main id="main" className="dash-main">
        <CityMap role={account.role} account={account} order={trackedOrder} onFeedback={addFeedback} />
        {view}
      </main>
    </div>
  );
}

function CustomerView({ onSelectOrder, onFeedback }) {
  return (
    <>
      <div className="dash-intro dash-section">
        <h1>Your market</h1>
        <p className="lede">Browse nearby groceries and follow your orders as the store prepares them.</p>
      </div>
      <CustomerOrders onSelectOrder={onSelectOrder} onFeedback={onFeedback} />
      <StoreList onFeedback={onFeedback} />
    </>
  );
}
