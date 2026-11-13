import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { formatMoney } from "../../data/mockData.js";
import SiteHeader from "../dash/SiteHeader.jsx";
import useSessionAccount from "../dash/useSessionAccount.js";
import { dashboardPathForRole } from "../reg/accountFactory.js";
import { saveAccount } from "../reg/accountSession.js";
import { appendConsoleMessage } from "../dash/consoleFeed.js";
import { useCart } from "./cartContext.js";
import { placeOrder } from "./orderApi.js";
import { productImage } from "./productImages.js";
import { storeFontClass } from "./storeFonts.js";
import "../dash/dashboard.css";
import "./shopping.css";

const DELIVERY_FEE_CENTS = 299;
const SERVICE_FEE_CENTS = 150;
const SMALL_ORDER_THRESHOLD_CENTS = 1_500;
const SMALL_ORDER_SURCHARGE_CENTS = 250;

function subtotalFor(basket) {
  return basket.lines.reduce((sum, line) => sum + line.priceCents * line.quantity, 0);
}

function surchargeFor(basket) {
  return subtotalFor(basket) < SMALL_ORDER_THRESHOLD_CENTS
    ? SMALL_ORDER_SURCHARGE_CENTS
    : 0;
}

export default function CheckoutPage() {
  const navigate = useNavigate();
  const { role: routeRole } = useParams();
  const { account, setAccount, loading } = useSessionAccount();
  const { baskets, setBaskets } = useCart();
  const addressInitialized = useRef(false);
  const [destination, setDestination] = useState("");
  const [deliveryMethod, setDeliveryMethod] = useState("leave-at-door");
  const [saveDefault, setSaveDefault] = useState(false);
  const [addressError, setAddressError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [resultDialog, setResultDialog] = useState(null);

  useEffect(() => {
    if (account && !addressInitialized.current) {
      setDestination(account.defaultAddress || "");
      addressInitialized.current = true;
    }
  }, [account]);

  const totals = useMemo(() => {
    const subtotalCents = baskets.reduce((sum, basket) => sum + subtotalFor(basket), 0);
    const deliveryFeeCents = DELIVERY_FEE_CENTS * baskets.length;
    const serviceFeeCents = SERVICE_FEE_CENTS * baskets.length;
    const smallOrderSurchargeCents = baskets.reduce(
      (sum, basket) => sum + surchargeFor(basket),
      0,
    );
    return {
      subtotalCents,
      deliveryFeeCents,
      serviceFeeCents,
      smallOrderSurchargeCents,
      totalCents: subtotalCents + deliveryFeeCents + serviceFeeCents + smallOrderSurchargeCents,
    };
  }, [baskets]);

  if (loading) {
    return <main className="dash-main" aria-live="polite">Loading checkout…</main>;
  }
  if (!account) return <Navigate to="/login" replace />;
  if (account.role !== "customer") return <Navigate to={dashboardPathForRole(account.role)} replace />;
  if (routeRole !== account.role) return <Navigate to={`${dashboardPathForRole(account.role)}/checkout`} replace />;

  const home = dashboardPathForRole(account.role);

  async function refreshAccount() {
    const session = await fetch("/api/session", { credentials: "include" });
    if (!session.ok) return;
    const updated = await session.json();
    saveAccount(updated);
    setAccount(updated);
  }

  async function submitOrders(event) {
    event.preventDefault();
    if (submitting || baskets.length === 0) return;
    const enteredAddress = destination.trim();
    if (!enteredAddress) {
      setAddressError("Enter the delivery address to place your order.");
      return;
    }

    setSubmitting(true);
    setAddressError("");
    const submittedBaskets = [...baskets];
    try {
      const results = await Promise.all(submittedBaskets.map((basket, index) => (
        placeOrder(
          basket.id,
          basket.lines,
          enteredAddress,
          saveDefault && index === 0,
        )
      )));
      const successfulIds = new Set();
      results.forEach((result, index) => {
        const basket = submittedBaskets[index];
        if (result.ok) {
          successfulIds.add(basket.id);
          appendConsoleMessage(
            `Order ${result.order.orderId} placed for ${basket.name}. Total: ${formatMoney(result.order.totalCents)}.`,
            "success",
          );
        } else {
          appendConsoleMessage(`The ${basket.name} order could not be placed.`, "error");
        }
      });

      if (successfulIds.size > 0) {
        setBaskets((current) => current.filter((basket) => !successfulIds.has(basket.id)));
        if (saveDefault) {
          try {
            await refreshAccount();
          } catch {
            appendConsoleMessage("The order was placed, but the account view could not be refreshed.", "error");
          }
        }
      }

      if (successfulIds.size === submittedBaskets.length) {
        setResultDialog({ type: "success" });
      } else {
        setResultDialog({
          type: "error",
          partial: successfulIds.size > 0,
        });
      }
    } catch {
      appendConsoleMessage("Your order could not be placed.", "error");
      setResultDialog({ type: "error", partial: false });
    } finally {
      setSubmitting(false);
    }
  }

  function goHome() {
    setResultDialog(null);
    navigate(home);
  }

  return (
    <div className="dashboard">
      <a className="skip-link" href="#main">Skip to content</a>
      <SiteHeader account={account} />
      <main id="main" className="dash-main">
        <button className="back-link" type="button" onClick={() => navigate(`${home}/cart`)}>
          <span aria-hidden="true">←</span> Back to cart
        </button>
        <section className="checkout-page" aria-labelledby="checkout-title">
          <div className="checkout-heading">
            <p className="checkout-eyebrow">Review and confirm</p>
            <h1 id="checkout-title">Checkout</h1>
            <p>Confirm your delivery preferences and review every grocery basket.</p>
          </div>

          {baskets.length === 0 && !resultDialog ? (
            <section className="checkout-empty">
              <h2>Your cart is empty</h2>
              <p>Add products before returning to checkout.</p>
              <button type="button" className="button-primary" onClick={() => navigate(home)}>
                Browse products
              </button>
            </section>
          ) : (
            <form className="checkout-layout" onSubmit={submitOrders} aria-busy={submitting}>
              <div className="checkout-main-column">
                <section className="checkout-card" aria-labelledby="delivery-details-title">
                  <div className="checkout-card-heading">
                    <span className="checkout-step" aria-hidden="true">1</span>
                    <div>
                      <h2 id="delivery-details-title">Delivery details</h2>
                      <p>Where and how should your groceries be delivered?</p>
                    </div>
                  </div>
                  <label className="field" htmlFor="checkout-address">
                    Delivery address
                    <input
                      id="checkout-address"
                      name="deliveryAddress"
                      type="text"
                      autoComplete="street-address"
                      required
                      value={destination}
                      onChange={(event) => {
                        setDestination(event.target.value);
                        setAddressError("");
                      }}
                      disabled={submitting}
                    />
                  </label>
                  {addressError && <p className="field-error" role="alert">{addressError}</p>}
                  <fieldset className="delivery-options" disabled={submitting}>
                    <legend>Delivery preference</legend>
                    <label className={deliveryMethod === "leave-at-door" ? "is-selected" : ""}>
                      <input
                        type="radio"
                        name="deliveryMethod"
                        value="leave-at-door"
                        checked={deliveryMethod === "leave-at-door"}
                        onChange={(event) => setDeliveryMethod(event.target.value)}
                      />
                      <span>
                        <strong>Leave at door</strong>
                        <small>The courier will leave the order at the entrance.</small>
                      </span>
                    </label>
                    <label className={deliveryMethod === "hand-it-to-me" ? "is-selected" : ""}>
                      <input
                        type="radio"
                        name="deliveryMethod"
                        value="hand-it-to-me"
                        checked={deliveryMethod === "hand-it-to-me"}
                        onChange={(event) => setDeliveryMethod(event.target.value)}
                      />
                      <span>
                        <strong>Hand it to me</strong>
                        <small>Meet the courier when the order arrives.</small>
                      </span>
                    </label>
                  </fieldset>
                  <label className="save-address-option">
                    <input
                      type="checkbox"
                      checked={saveDefault}
                      onChange={(event) => setSaveDefault(event.target.checked)}
                      disabled={submitting}
                    />
                    Save this as my default delivery address
                  </label>
                </section>

                <section className="checkout-card" aria-labelledby="cart-summary-title">
                  <div className="checkout-card-heading">
                    <span className="checkout-step" aria-hidden="true">2</span>
                    <div>
                      <h2 id="cart-summary-title">Cart summary</h2>
                      <p>Each grocery prepares and receives its own order.</p>
                    </div>
                  </div>
                  <div className="checkout-store-groups">
                    {baskets.map((basket) => {
                      const basketSubtotal = subtotalFor(basket);
                      const itemCount = basket.lines.reduce((sum, line) => sum + line.quantity, 0);
                      return (
                        <details key={basket.id} className="checkout-store-group" open>
                          <summary>
                            <span>
                              <strong className={storeFontClass(basket.id)}>{basket.name}</strong>
                              <small>{itemCount} {itemCount === 1 ? "item" : "items"}</small>
                            </span>
                            <span>{formatMoney(basketSubtotal)}</span>
                          </summary>
                          <ul className="checkout-product-list">
                            {basket.lines.map((line) => {
                              const image = productImage(line);
                              return (
                                <li key={line.id}>
                                  {image && <img src={image} alt="" />}
                                  <span className="checkout-product-copy">
                                    <strong>{line.name}</strong>
                                    <small>Quantity: {line.quantity}</small>
                                  </span>
                                  <span>{formatMoney(line.priceCents * line.quantity)}</span>
                                </li>
                              );
                            })}
                          </ul>
                          {surchargeFor(basket) > 0 && (
                            <p className="checkout-store-note">
                              A {formatMoney(SMALL_ORDER_SURCHARGE_CENTS)} small-order surcharge applies to this basket.
                            </p>
                          )}
                        </details>
                      );
                    })}
                  </div>
                </section>
              </div>

              <aside className="checkout-card order-total-card" aria-labelledby="order-total-title">
                <h2 id="order-total-title">Order total</h2>
                <dl className="order-total-lines">
                  <div><dt>Subtotal</dt><dd>{formatMoney(totals.subtotalCents)}</dd></div>
                  <div>
                    <dt>Delivery fee{baskets.length > 1 ? ` (${baskets.length})` : ""}</dt>
                    <dd>{formatMoney(totals.deliveryFeeCents)}</dd>
                  </div>
                  <div>
                    <dt>Service fee{baskets.length > 1 ? ` (${baskets.length})` : ""}</dt>
                    <dd>{formatMoney(totals.serviceFeeCents)}</dd>
                  </div>
                  {totals.smallOrderSurchargeCents > 0 && (
                    <div><dt>Small-order surcharge</dt><dd>{formatMoney(totals.smallOrderSurchargeCents)}</dd></div>
                  )}
                  <div className="order-total-final"><dt>Total</dt><dd>{formatMoney(totals.totalCents)}</dd></div>
                </dl>
                <p className="checkout-fee-note">
                  Delivery and service fees apply separately to each grocery order.
                </p>
                <button type="submit" className="button-primary checkout-place-order" disabled={submitting}>
                  {submitting ? "Placing order…" : "Place Order"}
                </button>
              </aside>
            </form>
          )}
        </section>
      </main>

      {resultDialog && createPortal(
        <div className="order-dialog-backdrop">
          <section
            className={`order-result-dialog is-${resultDialog.type}`}
            role="dialog"
            aria-modal="true"
            aria-labelledby="order-result-title"
            aria-describedby="order-result-message"
          >
            <span className="order-result-icon" aria-hidden="true">
              {resultDialog.type === "success" ? "✓" : "!"}
            </span>
            <h2 id="order-result-title">
              {resultDialog.type === "success" ? "Order placed" : "We couldn’t place every order"}
            </h2>
            <p id="order-result-message">
              {resultDialog.type === "success"
                ? "Your order has been placed and is waiting for the grocery store to accept it."
                : resultDialog.partial
                  ? "Some orders were placed, but one or more could not be placed. The remaining baskets are ready to try again."
                  : "Your order could not be placed. Your cart has been kept so you can try again."}
            </p>
            <div className="order-dialog-actions">
              {resultDialog.type === "error" && (
                <button type="button" className="button-primary" onClick={() => setResultDialog(null)} autoFocus>
                  Try Again
                </button>
              )}
              <button
                type="button"
                className={resultDialog.type === "error" ? "cart-add" : "button-primary"}
                onClick={goHome}
                autoFocus={resultDialog.type === "success"}
              >
                Go to Home Page
              </button>
            </div>
          </section>
        </div>,
        document.body,
      )}
    </div>
  );
}
