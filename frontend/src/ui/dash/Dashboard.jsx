import { useCallback, useEffect } from "react";
import { Navigate, useParams } from "react-router-dom";
import { dashboardPathForRole } from "../reg/accountFactory.js";
import StoreOrders from "../ord/StoreOrders.jsx";
import StoreList from "../ord/StoreList.jsx";
import CityMap from "./CityMap.jsx";
import OperatorDashboard from "./OperatorDashboard.jsx";
import RoleDashboardView from "./RoleDashboardView.jsx";
import SiteHeader from "./SiteHeader.jsx";
import { appendConsoleMessage } from "./consoleFeed.js";
import useSessionAccount from "./useSessionAccount.js";
import "./dashboard.css";
import ResetControl from "./ResetControl.jsx";

export default function Dashboard() {
  const { role: routeRole } = useParams();
  const { account, loading } = useSessionAccount();

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
    view = <CustomerView onFeedback={addFeedback} />;
  } else if (account.role === "groceries") {
    view = <StoreOrders onFeedback={addFeedback} />;
  } else if (account.role === "operator") {
    view = <OperatorDashboard onFeedback={addFeedback} />;
  } else {
    view = <RoleDashboardView role={account.role} onFeedback={addFeedback} />;
  }

  return (
    <div className="dashboard">
      <a className="skip-link" href="#main">Skip to content</a>
      <SiteHeader account={account} />
      <main id="main" className="dash-main">
        {account.role === "customer" ? (
          <>
            {view}
            <CityMap role={account.role} account={account} order={null} onFeedback={addFeedback} />
          </>
        ) : (
          <>
            <CityMap role={account.role} account={account} order={null} onFeedback={addFeedback} />
            {account.role === "operator" ? <ResetControl onFeedback={addFeedback} /> : null}
            {view}
          </>
        )}
      </main>
    </div>
  );
}

function CustomerView({ onFeedback }) {
  return (
    <StoreList onFeedback={onFeedback} />
  );
}
