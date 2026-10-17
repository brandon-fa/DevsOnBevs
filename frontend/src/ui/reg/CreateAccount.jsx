import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ROLE_LABELS, ROLES, accountIssues, dashboardPathForRole, factoryFor } from "./accountFactory.js";
import { saveAccount } from "./accountSession.js";
import "./create-account.css";

const EMPTY_FORM = {
  fullName: "",
  email: "",
  password: "",
  phone: "",
  role: "",
};

export default function CreateAccount() {
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    document.title = "Create account · Little Isles";
  }, []);

  function update(key) {
    return (event) => {
      const value = event.target.value;
      setForm((current) => ({ ...current, [key]: value }));
      setErrors((current) => ({ ...current, [key]: undefined, form: undefined }));
    };
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const issues = accountIssues(form);
    if (!ROLES.includes(form.role)) issues.role = "Choose a role.";

    if (Object.values(issues).some(Boolean)) {
      setErrors(issues);
      return;
    }

    try {
      factoryFor(form.role).register(form);
      const response = await fetch("/api/accounts", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          fullName: form.fullName.trim(),
          email: form.email.trim(),
          password: form.password,
          phone: form.phone.trim(),
          role: form.role,
        }),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        setErrors({ form: payload.error || "The account could not be saved." });
        return;
      }
      const loginResponse = await fetch("/api/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: form.email.trim(), password: form.password }),
      });
      const authenticatedAccount = await loginResponse.json().catch(() => ({}));
      if (!loginResponse.ok) {
        setErrors({ form: authenticatedAccount.error || "Account created. Please log in." });
        navigate("/login");
        return;
      }
      saveAccount(authenticatedAccount);
      navigate(dashboardPathForRole(authenticatedAccount.role));
    } catch (error) {
      setErrors(error.issues ?? { form: error.message || "The account could not be saved." });
    }
  }

  const messages = Object.values(errors).filter(Boolean);

  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <main id="main" className="create-page">
        <section className="brand-panel" aria-label="Little Isles">
          <div>
            <p className="wordmark">Little Isles</p>
            <div className="brand-rule" aria-hidden="true" />
          </div>
          <div className="brand-copy">
            <p className="eyebrow">Ethnic groceries</p>
            <h1 className="brand-title">The market, delivered.</h1>
            <p className="lede">
              Order from grocers across the city. A courier brings the basket to your door.
            </p>
          </div>
        </section>

        <section className="form-panel" aria-labelledby="create-account-title">
          <div className="form-card">
            <h2 id="create-account-title">Create an account</h2>
            <p className="form-intro">Same form for every role. This account holds one.</p>
            <p className="form-intro"><a href="/login">Already have an account? Log in</a></p>

            <form className="account-form" onSubmit={handleSubmit} noValidate>
              {messages.length > 0 && (
                <div className="form-alert" role="alert">
                  <ul>
                    {messages.map((message) => (
                      <li key={message}>{message}</li>
                    ))}
                  </ul>
                </div>
              )}

              <label className="field">
                <span>Full name</span>
                <input
                  id="full-name"
                  name="fullName"
                  type="text"
                  autoComplete="name"
                  value={form.fullName}
                  onChange={update("fullName")}
                  aria-invalid={errors.fullName ? "true" : "false"}
                  aria-describedby={errors.fullName ? "full-name-error" : undefined}
                />
                {errors.fullName && (
                  <span id="full-name-error" className="field-error">
                    {errors.fullName}
                  </span>
                )}
              </label>

              <label className="field">
                <span>Email</span>
                <input
                  id="email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  spellCheck="false"
                  value={form.email}
                  onChange={update("email")}
                  aria-invalid={errors.email ? "true" : "false"}
                  aria-describedby={errors.email ? "email-error" : undefined}
                />
                {errors.email && (
                  <span id="email-error" className="field-error">
                    {errors.email}
                  </span>
                )}
              </label>

              <label className="field">
                <span>Password</span>
                <input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete="new-password"
                  value={form.password}
                  onChange={update("password")}
                  aria-invalid={errors.password ? "true" : "false"}
                  aria-describedby={errors.password ? "password-error" : undefined}
                />
                {errors.password && (
                  <span id="password-error" className="field-error">
                    {errors.password}
                  </span>
                )}
              </label>

              <label className="field">
                <span>Phone</span>
                <input
                  id="phone"
                  name="phone"
                  type="tel"
                  autoComplete="tel"
                  value={form.phone}
                  onChange={update("phone")}
                  aria-invalid={errors.phone ? "true" : "false"}
                  aria-describedby={errors.phone ? "phone-error" : undefined}
                />
                {errors.phone && (
                  <span id="phone-error" className="field-error">
                    {errors.phone}
                  </span>
                )}
              </label>

              <fieldset className="role-fieldset">
                <legend>Role</legend>
                <p className="field-hint" id="role-hint">
                  This account holds one role.
                </p>
                <div className="role-options">
                  {ROLES.map((role) => (
                    <label key={role} className="role-option">
                      <input
                        type="radio"
                        name="role"
                        value={role}
                        checked={form.role === role}
                        onChange={update("role")}
                        aria-describedby={errors.role ? "role-error" : "role-hint"}
                      />
                      <span>{ROLE_LABELS[role]}</span>
                    </label>
                  ))}
                </div>
                {errors.role && (
                  <span id="role-error" className="field-error">
                    {errors.role}
                  </span>
                )}
              </fieldset>

              <button className="button-primary" type="submit">
                Create account
              </button>
            </form>
          </div>
        </section>
      </main>
    </>
  );
}
