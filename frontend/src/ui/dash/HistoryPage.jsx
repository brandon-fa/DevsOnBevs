import { useEffect, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { dashboardPathForRole } from "../reg/accountFactory.js";
import ConsolePanel from "./ConsolePanel.jsx";
import SiteHeader from "./SiteHeader.jsx";
import { readConsoleMessages, subscribeConsole } from "./consoleFeed.js";
import useSessionAccount from "./useSessionAccount.js";
import "./dashboard.css";

export default function HistoryPage() {
  const navigate = useNavigate();
  const { role: routeRole } = useParams();
  const { account, loading } = useSessionAccount();
  const [messages, setMessages] = useState(readConsoleMessages);

  useEffect(() => subscribeConsole(setMessages), []);

  if (loading) {
    return <main className="dash-main" aria-live="polite">Loading history…</main>;
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
        <div className="dash-intro dash-section">
          <h1>History</h1>
          <p className="lede">Feedback from actions on your dashboard.</p>
        </div>
        <ConsolePanel messages={messages} />
      </main>
    </div>
  );
}
