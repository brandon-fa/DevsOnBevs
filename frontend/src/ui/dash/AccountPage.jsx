import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { ROLE_LABELS, dashboardPathForRole } from "../reg/accountFactory.js";
import { saveAccount } from "../reg/accountSession.js";
import SiteHeader from "./SiteHeader.jsx";
import useSessionAccount from "./useSessionAccount.js";
import "./dashboard.css";

export default function AccountPage() {
  const navigate = useNavigate();
  const { role: routeRole } = useParams();
  const { account, setAccount, loading } = useSessionAccount();

  if (loading) {
    return <main className="dash-main" aria-live="polite">Loading your account…</main>;
  }
  if (!account) return <Navigate to="/login" replace />;
  if (routeRole !== account.role) return <Navigate to={`${dashboardPathForRole(account.role)}/account`} replace />;

  return (
    <div className="dashboard">
      <a className="skip-link" href="#main">Skip to content</a>
      <SiteHeader account={account} />
      <main id="main" className="dash-main">
        <button className="back-link" type="button" onClick={() => navigate(dashboardPathForRole(account.role))}>
          <span aria-hidden="true">←</span> Back
        </button>
        <AccountForm account={account} onUpdated={setAccount} />
      </main>
    </div>
  );
}

function AccountForm({ account, onUpdated }) {
  const [phone, setPhone] = useState(account.phone);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [error, setError] = useState("");
  const [passwordNotice, setPasswordNotice] = useState("");
  const [saved, setSaved] = useState(false);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (!passwordNotice) return undefined;
    const timer = window.setTimeout(() => setPasswordNotice(""), 3000);
    return () => window.clearTimeout(timer);
  }, [passwordNotice]);

  async function onSubmit(event) {
    event.preventDefault();
    setError("");
    setPasswordNotice("");
    setSaved(false);
    if (!phone.trim()) {
      setError("Enter a phone number.");
      return;
    }
    if (newPassword && !currentPassword) {
      setError("Enter your current password to set a new one.");
      return;
    }
    setPending(true);
    try {
      const response = await fetch("/api/account", {
        method: "PATCH",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          phone: phone.trim(),
          currentPassword,
          newPassword,
        }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        const message = body.error || "The account could not be saved.";
        if (message.toLowerCase().includes("password")) {
          setPasswordNotice(message);
        } else {
          setError(message);
        }
        return;
      }
      saveAccount(body);
      onUpdated(body);
      setCurrentPassword("");
      setNewPassword("");
      setSaved(true);
    } catch {
      setError("The account could not be saved.");
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="dash-section account-page">
      <h1>Account</h1>
      <p className="lede">Update your phone number or password. Your role stays the same.</p>
      <dl className="account-facts">
        <div>
          <dt>Name</dt>
          <dd>{account.fullName}</dd>
        </div>
        <div>
          <dt>Email</dt>
          <dd>{account.email}</dd>
        </div>
        <div>
          <dt>Role</dt>
          <dd>{ROLE_LABELS[account.role]}</dd>
        </div>
      </dl>
      <form className="account-form" onSubmit={onSubmit}>
        <label className="field" htmlFor="account-phone">
          Phone
          <input id="account-phone" name="phone" type="tel" autoComplete="tel" value={phone} onChange={(event) => setPhone(event.target.value)} />
        </label>
        <label className="field" htmlFor="account-current-password">
          Current password
          <input id="account-current-password" name="currentPassword" type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} />
        </label>
        <label className="field" htmlFor="account-new-password">
          New password
          <input id="account-new-password" name="newPassword" type="password" autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} />
        </label>
        {error && <p className="form-alert" role="alert">{error}</p>}
        {saved && <p className="form-alert" role="status">Account updated.</p>}
        {passwordNotice && createPortal(
          <p className="password-toast" role="alert">{passwordNotice}</p>,
          document.body,
        )}
        <button className="button-primary" type="submit" disabled={pending}>
          {pending ? "Saving…" : "Save changes"}
        </button>
      </form>
    </section>
  );
}
