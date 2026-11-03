import { useState } from "react";
import { useNavigate, useParams, Navigate } from "react-router-dom";
import { formatMoney } from "../../data/mockData.js";
import { dashboardPathForRole } from "../reg/accountFactory.js";
import SiteHeader from "../dash/SiteHeader.jsx";
import useSessionAccount from "../dash/useSessionAccount.js";
import { appendConsoleMessage } from "../dash/consoleFeed.js";
import { placeOrder } from "./orderApi.js";
import { useCart } from "./cartContext.js";
import "../dash/dashboard.css";
import "./shopping.css";

export default function CartPage() {
  const navigate = useNavigate();
  const { role: routeRole } = useParams();
  const { account, loading } = useSessionAccount();
  const { cart, setCart, store } = useCart();
  const [askingAddress, setAskingAddress] = useState(false);
  const [destination, setDestination] = useState("");
  const [addressError, setAddressError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (loading) {
    return <main className="dash-main" aria-live="polite">Loading your cart…</main>;
  }
  if (!account) return <Navigate to="/login" replace />;
  if (account.role !== "customer") return <Navigate to={dashboardPathForRole(account.role)} replace />;
  if (routeRole !== account.role) return <Navigate to={`${dashboardPathForRole(account.role)}/cart`} replace />;

  function changeQuantity(productId, quantity) {
    if (submitting) return;
    setCart((previous) => {
      if (quantity < 1) return previous.filter((line) => line.id !== productId);
      return previous.map((line) => (line.id === productId ? { ...line, quantity } : line));
    });
  }

  function removeLine(productId) {
    if (submitting) return;
    setCart((previous) => previous.filter((line) => line.id !== productId));
  }

  function beginOrder() {
    if (cart.length === 0) {
      appendConsoleMessage("Your cart is empty. Add a product before placing your order.", "error");
      return;
    }
    if (!store) {
      appendConsoleMessage("Choose a grocery store before placing an order.", "error");
      return;
    }
    if (!store.open) {
      appendConsoleMessage(`${store.name} is closed. Choose an open grocery store to place your order.`, "error");
      return;
    }
    setAskingAddress(true);
    setAddressError("");
  }

  async function submitOrder() {
    if (submitting) return;
    if (!destination.trim()) {
      setAddressError("Enter the delivery address to place your order.");
      return;
    }
    setSubmitting(true);
    setAddressError("");
    try {
      const result = await placeOrder(store.id, cart, destination);
      if (result.ok) {
        appendConsoleMessage("Order " + result.order.orderId + " placed.", "success");
        setCart([]);
        navigate(dashboardPathForRole(account.role));
      } else {
        appendConsoleMessage(result.error || "Your order could not be placed. Please try again.", "error");
      }
    } catch {
      appendConsoleMessage("Your order could not be confirmed. Check your connection before trying again.", "error");
    } finally {
      setSubmitting(false);
    }
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
          {store && <p className="cart-store">{store.name}</p>}
          {cart.length === 0 && <p>Your cart is empty.</p>}
          <ul className="cart-lines">
            {cart.map((line) => (
              <li key={line.id} className="cart-line">
                <div>
                  <p className="product-name">{line.name}</p>
                  <p className="product-price">{formatMoney(line.priceCents * line.quantity)}</p>
                </div>
                <div className="cart-line-actions">
                  <div className="qty-control">
                    <button
                      type="button"
                      aria-label={`Decrease ${line.name}`}
                      disabled={submitting}
                      onClick={() => changeQuantity(line.id, line.quantity - 1)}
                    >
                      −
                    </button>
                    <span>{line.quantity}</span>
                    <button
                      type="button"
                      aria-label={`Increase ${line.name}`}
                      disabled={submitting}
                      onClick={() => changeQuantity(line.id, line.quantity + 1)}
                    >
                      +
                    </button>
                  </div>
                  <button type="button" className="cart-remove" disabled={submitting} onClick={() => removeLine(line.id)}>
                    Remove
                  </button>
                </div>
              </li>
            ))}
          </ul>
          {!askingAddress && (
            <button type="button" className="button-primary cart-place" onClick={beginOrder} disabled={submitting || cart.length === 0}>
              Place order
            </button>
          )}
          {askingAddress && (
            <form
              className="cart-address"
              onSubmit={(event) => {
                event.preventDefault();
                submitOrder();
              }}
            >
              <h2>Where should we deliver this order?</h2>
              <label className="field" htmlFor="delivery-address">
                Delivery address
                <input
                  id="delivery-address"
                  name="deliveryAddress"
                  type="text"
                  autoComplete="street-address"
                  required
                  value={destination}
                  onChange={(event) => setDestination(event.target.value)}
                  disabled={submitting}
                />
              </label>
              {addressError && <p className="field-error" role="alert">{addressError}</p>}
              <button type="submit" className="button-primary" disabled={submitting}>
                {submitting ? "Placing order…" : "Place order"}
              </button>
            </form>
          )}
        </section>
      </main>
    </div>
  );
}
