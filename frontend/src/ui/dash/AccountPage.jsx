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
  const [defaultAddress, setDefaultAddress] = useState(account.defaultAddress || "");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [passwordNotice, setPasswordNotice] = useState("");
  const [saved, setSaved] = useState("");
  const [pending, setPending] = useState(false);

  useEffect(() => {
    setDefaultAddress(account.defaultAddress || "");
  }, [account.defaultAddress]);

  useEffect(() => {
    if (!passwordNotice) return undefined;
    const timer = window.setTimeout(() => setPasswordNotice(""), 3000);
    return () => window.clearTimeout(timer);
  }, [passwordNotice]);

  async function onSubmit(event) {
    event.preventDefault();
    setError("");
    setPasswordNotice("");
    setSaved("");
    if (!phone.trim()) {
      setError("Enter a phone number.");
      return;
    }
    if (currentPassword && !newPassword) {
      setError("Enter a new password. Filling in only the current password does not change it.");
      return;
    }
    if (newPassword && !currentPassword) {
      setError("Enter your current password to set a new one.");
      return;
    }
    if (newPassword && newPassword !== confirmPassword) {
      setError("New password and confirm password do not match.");
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
          defaultAddress: account.role === "customer" ? defaultAddress.trim() : "",
        }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        const message = body.error || "The account could not be saved.";
        if (newPassword && currentPassword) {
          setPasswordNotice(
            message.toLowerCase().includes("incorrect")
              ? "Current password is incorrect."
              : message,
          );
        } else if (message.toLowerCase().includes("password")) {
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
      setConfirmPassword("");
      setSaved(newPassword ? "password" : "profile");
    } catch {
      setError("The account could not be saved.");
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="dash-section account-page">
      <h1>Account</h1>
      <p className="lede">
        {account.role === "customer"
          ? "Update your phone number, default address, or password. A default address is optional."
          : "Update your phone number or password. Your role stays the same."}
      </p>
      <div className="account-layout">
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
        {account.role === "customer" && (
          <label className="field" htmlFor="account-address">
            Default address
            <input
              id="account-address"
              name="defaultAddress"
              type="text"
              autoComplete="street-address"
              value={defaultAddress}
              onChange={(event) => setDefaultAddress(event.target.value)}
              aria-describedby="account-address-hint"
            />
            <span className="field-hint" id="account-address-hint">
              Leave this blank if you prefer to enter a different address for each order.
            </span>
          </label>
        )}
        <label className="field" htmlFor="account-current-password">
          Current password
          <input id="account-current-password" name="currentPassword" type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} />
        </label>
        <label className="field" htmlFor="account-new-password">
          New password
          <input id="account-new-password" name="newPassword" type="password" autoComplete="new-password" value={newPassword} onChange={(event) => {
            setNewPassword(event.target.value);
            if (!event.target.value) setConfirmPassword("");
          }} />
        </label>
        {newPassword && (
          <label className="field" htmlFor="account-confirm-password">
            Confirm password
            <input id="account-confirm-password" name="confirmPassword" type="password" autoComplete="new-password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} />
          </label>
        )}
        {error && <p className="form-alert" role="alert">{error}</p>}
        {saved === "profile" && <p className="form-alert" role="status">Account saved. Your password was not changed.</p>}
        {saved === "password" && <p className="form-alert" role="status">Password updated.</p>}
        {passwordNotice && createPortal(
          <p className="password-toast" role="alert">{passwordNotice}</p>,
          document.body,
        )}
        <button className="button-primary" type="submit" disabled={pending}>
          {pending ? "Saving…" : "Save changes"}
        </button>
      </form>
      </div>
    </section>
  );
}
