import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { dashboardPathForRole } from "../reg/accountFactory.js";
import SiteHeader from "../dash/SiteHeader.jsx";
import useSessionAccount from "../dash/useSessionAccount.js";
import { appendConsoleMessage } from "../dash/consoleFeed.js";
import CustomerOrders from "./CustomerOrders.jsx";
import "../dash/dashboard.css";
import "./shopping.css";

export default function TrackOrdersPage() {
  const navigate = useNavigate();
  const { role: routeRole } = useParams();
  const [searchParams] = useSearchParams();
  const { account, loading } = useSessionAccount();
  const storeId = searchParams.get("store");

  if (loading) {
    return <main className="dash-main" aria-live="polite">Loading your orders…</main>;
  }
  if (!account) return <Navigate to="/login" replace />;
  if (account.role !== "customer") return <Navigate to={dashboardPathForRole(account.role)} replace />;
  if (routeRole !== account.role) return <Navigate to={`${dashboardPathForRole(account.role)}/orders`} replace />;

  return (
    <div className="dashboard">
      <a className="skip-link" href="#main">Skip to content</a>
      <SiteHeader account={account} />
      <main id="main" className="dash-main">
        <button className="back-link" type="button" onClick={() => navigate(dashboardPathForRole(account.role))}>
          <span aria-hidden="true">←</span> Back
        </button>
        <CustomerOrders storeId={storeId} onFeedback={appendConsoleMessage} />
      </main>
    </div>
  );
}
