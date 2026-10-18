import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { saveAccount } from "./accountSession.js";
import { dashboardPathForRole } from "./accountFactory.js";
import "./create-account.css";

export default function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { document.title = "Log in · Little Isles"; }, []);

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const response = await fetch("/api/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ email: email.trim(), password }),
      });
      const account = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(account.error || "Email or password is incorrect.");
        return;
      }
      saveAccount(account);
      navigate(dashboardPathForRole(account.role));
    } catch {
      setError("Login is temporarily unavailable. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>
      <main id="main" className="create-page">
        <section className="brand-panel" aria-label="Little Isles">
          <div><p className="wordmark">Little Isles</p><div className="brand-rule" aria-hidden="true" /></div>
          <div className="brand-copy">
            <p className="eyebrow">Ethnic groceries</p>
            <h1 className="brand-title">The market, delivered.</h1>
            <p className="lede">Order from grocers across the city. A courier brings the basket to your door.</p>
          </div>
        </section>
        <section className="form-panel" aria-labelledby="login-title">
          <div className="form-card">
            <h2 id="login-title">Welcome back</h2>
            <p className="form-intro">Log in to your Little Isles account.</p>
            <form className="account-form" onSubmit={handleSubmit}>
              {error && <div className="form-alert" role="alert">{error}</div>}
              <label className="field" htmlFor="login-email">
                <span>Email</span>
                <input id="login-email" name="email" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} />
              </label>
              <label className="field" htmlFor="login-password">
                <span>Password</span>
                <input id="login-password" name="password" type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} />
              </label>
              <button className="button-primary" type="submit" disabled={busy}>{busy ? "Logging in…" : "Log in"}</button>
            </form>
            <p className="form-intro" style={{ marginTop: "1rem", marginBottom: 0 }}>New to Little Isles? <Link to="/register">Create an account</Link></p>
          </div>
        </section>
      </main>
    </>
  );
}
