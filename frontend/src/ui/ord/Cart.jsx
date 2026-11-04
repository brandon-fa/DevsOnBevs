import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate, useParams, Navigate } from "react-router-dom";
import { formatMoney } from "../../data/mockData.js";
import { dashboardPathForRole } from "../reg/accountFactory.js";
import { saveAccount } from "../reg/accountSession.js";
import SiteHeader from "../dash/SiteHeader.jsx";
import useSessionAccount from "../dash/useSessionAccount.js";
import { appendConsoleMessage } from "../dash/consoleFeed.js";
import { placeOrder } from "./orderApi.js";
import OrderPlacedAnimation from "./OrderPlacedAnimation.jsx";
import { useCart } from "./cartContext.js";
import { storeFontClass } from "./storeFonts.js";
import "../dash/dashboard.css";
import "./shopping.css";

export default function CartPage() {
  const navigate = useNavigate();
  const { role: routeRole } = useParams();
  const { account, setAccount, loading } = useSessionAccount();
  const { baskets, setBaskets } = useCart();
  const [orderingId, setOrderingId] = useState(null);
  const [destination, setDestination] = useState("");
  const [addressError, setAddressError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState("");
  const [success, setSuccess] = useState("");
  const [orderError, setOrderError] = useState("");
  const [selectedStoreId, setSelectedStoreId] = useState(null);
  const [askDefault, setAskDefault] = useState(false);

  useEffect(() => {
    if (!notice) return undefined;
    const timer = window.setTimeout(() => setNotice(""), 3000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  useEffect(() => {
    if (!success) return undefined;
    const timer = window.setTimeout(() => setSuccess(""), 5000);
    return () => window.clearTimeout(timer);
  }, [success]);

  if (loading) {
    return <main className="dash-main" aria-live="polite">Loading your cart…</main>;
  }
  if (!account) return <Navigate to="/login" replace />;
  if (account.role !== "customer") return <Navigate to={dashboardPathForRole(account.role)} replace />;
  if (routeRole !== account.role) return <Navigate to={`${dashboardPathForRole(account.role)}/cart`} replace />;

  function updateBasket(storeId, changeLines) {
    if (submitting) return;
    setBaskets((previous) => previous.flatMap((basket) => {
      if (basket.id !== storeId) return [basket];
      const lines = changeLines(basket.lines);
      if (lines.length === 0) return [];
      return [{ ...basket, lines }];
    }));
  }

  function changeQuantity(storeId, productId, quantity) {
    updateBasket(storeId, (lines) => {
      if (quantity < 1) return lines.filter((line) => line.id !== productId);
      return lines.map((line) => (line.id === productId ? { ...line, quantity } : line));
    });
  }

  function removeLine(storeId, productId) {
    updateBasket(storeId, (lines) => lines.filter((line) => line.id !== productId));
  }

  function beginOrder(basket) {
    if (basket.lines.length === 0) {
      const message = "Your cart is empty. Add a product before placing your order.";
      setNotice(message);
      appendConsoleMessage(message, "error");
      return;
    }
    if (!basket.open) {
      const message = `${basket.name} is closed. Choose an open grocery store to place your order.`;
      setNotice(message);
      appendConsoleMessage(message, "error");
      return;
    }
    setOrderingId(basket.id);
    setDestination(account.defaultAddress || "");
    setAddressError("");
    setAskDefault(false);
  }

  async function submitOrder(basket, saveAsDefault) {
    if (submitting) return;
    if (!destination.trim()) {
      setAddressError("Enter the delivery address to place your order.");
      return;
    }
    const entered = destination.trim();
    const savedDefault = (account.defaultAddress || "").trim();
    if (saveAsDefault === undefined && entered !== savedDefault) {
      setAskDefault(true);
      return;
    }
    setSubmitting(true);
    setAddressError("");
    setOrderError("");
    setAskDefault(false);
    try {
      const result = await placeOrder(basket.id, basket.lines, entered, saveAsDefault === true);
      if (saveAsDefault && result.ok) {
        const session = await fetch("/api/session", { credentials: "include" });
        if (session.ok) {
          const updated = await session.json();
          saveAccount(updated);
          setAccount(updated);
        }
      }
      if (result.ok) {
        const message = basket.name + " order placed.";
        appendConsoleMessage("Order " + result.order.orderId + " placed for " + basket.name + ".", "success");
        const remaining = baskets.filter((item) => item.id !== basket.id);
        setBaskets(remaining);
        setOrderingId(null);
        setDestination("");
        setSuccess(message);
      } else {
        const message = result.error || "Your order could not be placed. Please try again.";
        setOrderError(message);
        appendConsoleMessage(message, "error");
      }
    } catch {
      const message = "Your order could not be confirmed. Check your connection before trying again.";
      setOrderError(message);
      appendConsoleMessage(message, "error");
    } finally {
      setSubmitting(false);
    }
  }

  const visibleId = baskets.some((basket) => basket.id === selectedStoreId)
    ? selectedStoreId
    : baskets[0]?.id;
  const visibleBasket = baskets.find((basket) => basket.id === visibleId);

  if (success) {
    return (
      <div className="dashboard">
        <a className="skip-link" href="#main">Skip to content</a>
        <SiteHeader account={account} />
        <main id="main" className="dash-main">
          <OrderPlacedAnimation />
        </main>
      </div>
    );
  }

  return (
    <div className="dashboard">
      <a className="skip-link" href="#main">Skip to content</a>
      <SiteHeader account={account} />
      <main id="main" className="dash-main">
        <button className="back-link" type="button" onClick={() => navigate(dashboardPathForRole(account.role))}>
          <span aria-hidden="true">←</span> Back
        </button>
        <section className="cart-page" aria-busy={submitting} aria-labelledby="cart-title">
          <h1 id="cart-title">Your cart</h1>
          {baskets.length > 0 && (
            <div className="cart-store-labels">
              {baskets.map((basket) => (
                <button
                  key={basket.id}
                  type="button"
                  className={`store-chip ${storeFontClass(basket.id)} ${basket.open ? "is-open" : "is-closed"} ${basket.id === visibleId ? "is-selected" : ""}`}
                  aria-pressed={basket.id === visibleId}
                  onClick={() => setSelectedStoreId(basket.id)}
                >
                  {basket.name}
                </button>
              ))}
            </div>
          )}
          {baskets.length === 0 && <p className="cart-empty">Your cart is empty.</p>}
          {visibleBasket && (
            <section key={visibleBasket.id} id={"basket-" + visibleBasket.id} className="cart-basket" aria-label={visibleBasket.name}>
              <ul className="cart-lines">
                {visibleBasket.lines.map((line) => (
                  <li key={line.id} className="cart-line">
                    <div>
                      <p className="product-name">{line.name} (x{line.quantity})</p>
                      <p className="product-price">{formatMoney(line.priceCents * line.quantity)}</p>
                    </div>
                    <div className="cart-line-actions">
                      <div className="qty-control">
                        <button
                          type="button"
                          aria-label={`Decrease ${line.name}`}
                          disabled={submitting}
                          onClick={() => changeQuantity(visibleBasket.id, line.id, line.quantity - 1)}
                        >
                          −
                        </button>
                        <span>{line.quantity}</span>
                        <button
                          type="button"
                          aria-label={`Increase ${line.name}`}
                          disabled={submitting}
                          onClick={() => changeQuantity(visibleBasket.id, line.id, line.quantity + 1)}
                        >
                          +
                        </button>
                      </div>
                      <button type="button" className="cart-remove" disabled={submitting} onClick={() => removeLine(visibleBasket.id, line.id)}>
                        Remove
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
              <p className="cart-total">
                Total <strong>{formatMoney(visibleBasket.lines.reduce((sum, line) => sum + line.priceCents * line.quantity, 0))}</strong>
              </p>
              {orderingId !== visibleBasket.id && (
                <div className="cart-actions">
                  <button type="button" className="button-primary cart-place" onClick={() => beginOrder(visibleBasket)} disabled={submitting}>
                    Place {visibleBasket.name} order
                  </button>
                </div>
              )}
              {orderingId === visibleBasket.id && (
                <form
                  className="cart-address"
                  onSubmit={(event) => {
                    event.preventDefault();
                    submitOrder(visibleBasket);
                  }}
                >
                  <h2>Where should we deliver the {visibleBasket.name} order?</h2>
                  <label className="field" htmlFor={"delivery-address-" + visibleBasket.id}>
                    Delivery address
                    <input
                      id={"delivery-address-" + visibleBasket.id}
                      name="deliveryAddress"
                      type="text"
                      autoComplete="street-address"
                      required
                      value={destination}
                      onChange={(event) => {
                        setDestination(event.target.value);
                        setAskDefault(false);
                      }}
                      disabled={submitting}
                    />
                  </label>
                  {addressError && <p className="field-error" role="alert">{addressError}</p>}
                  {orderError && <p className="field-error" role="alert">{orderError}</p>}
                  {askDefault && (
                    <div className="cart-default-prompt" role="group" aria-label="Save as default address">
                      <p>Save this address as your default delivery address?</p>
                      <div className="cart-actions">
                        <button type="button" className="button-primary" disabled={submitting} onClick={() => submitOrder(visibleBasket, true)}>
                          Yes, save it
                        </button>
                        <button type="button" className="cart-add" disabled={submitting} onClick={() => submitOrder(visibleBasket, false)}>
                          No, just this order
                        </button>
                      </div>
                    </div>
                  )}
                  {!askDefault && (
                    <button type="submit" className="button-primary" disabled={submitting}>
                      {submitting ? "Placing order…" : "Place order"}
                    </button>
                  )}
                </form>
              )}
            </section>
          )}
          <div className="cart-actions">
            <button
              type="button"
              className="cart-add"
              onClick={() => navigate(dashboardPathForRole(account.role))}
            >
              + Add products
            </button>
          </div>
          {notice && createPortal(
            <p className="password-toast" role="alert">{notice}</p>,
            document.body,
          )}
        </section>
      </main>
    </div>
  );
}
