import { useEffect, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { formatMoney } from "../../data/mockData.js";
import { dashboardPathForRole } from "../reg/accountFactory.js";
import SiteHeader from "../dash/SiteHeader.jsx";
import useSessionAccount from "../dash/useSessionAccount.js";
import { useCart } from "./cartContext.js";
import { loadStores } from "./orderApi.js";
import { productImage } from "./productImages.js";
import { storeFontClass } from "./storeFonts.js";
import "../dash/dashboard.css";
import "./shopping.css";

export default function CartPage() {
  const navigate = useNavigate();
  const { role: routeRole } = useParams();
  const { account, loading } = useSessionAccount();
  const { baskets, setBaskets } = useCart();
  const [selectedStoreId, setSelectedStoreId] = useState(null);
  const [liveStores, setLiveStores] = useState({});

  useEffect(() => {
    const controller = new AbortController();
    let timer;
    async function load() {
      try {
        const stores = await loadStores(controller.signal);
        const byId = {};
        stores.forEach((store) => {
          byId[store.id] = store;
        });
        setLiveStores(byId);
      } catch {
        // Keep the last known statuses; the server still refuses orders at a store that is not open.
      } finally {
        if (!controller.signal.aborted) timer = window.setTimeout(load, 5000);
      }
    }
    load();
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, []);

  if (loading) {
    return <main className="dash-main" aria-live="polite">Loading your cart…</main>;
  }
  if (!account) return <Navigate to="/login" replace />;
  if (account.role !== "customer") return <Navigate to={dashboardPathForRole(account.role)} replace />;
  if (routeRole !== account.role) return <Navigate to={`${dashboardPathForRole(account.role)}/cart`} replace />;

  function updateBasket(storeId, changeLines) {
    setBaskets((previous) => previous.flatMap((basket) => {
      if (basket.id !== storeId) return [basket];
      const lines = changeLines(basket.lines);
      return lines.length === 0 ? [] : [{ ...basket, lines }];
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

  function basketStatus(basket) {
    const live = liveStores[basket.id];
    return live ? live.status || (live.open ? "open" : "closed") : basket.open ? "open" : "closed";
  }

  const visibleId = baskets.some((basket) => basket.id === selectedStoreId)
    ? selectedStoreId
    : baskets[0]?.id;
  const visibleBasket = baskets.find((basket) => basket.id === visibleId);
  const itemCount = visibleBasket?.lines.reduce((sum, line) => sum + line.quantity, 0) || 0;
  const storeSubtotal = visibleBasket?.lines.reduce(
    (sum, line) => sum + line.priceCents * line.quantity,
    0,
  ) || 0;
  const home = dashboardPathForRole(account.role);

  return (
    <div className="dashboard">
      <a className="skip-link" href="#main">Skip to content</a>
      <SiteHeader account={account} />
      <main id="main" className="dash-main">
        <button className="back-link" type="button" onClick={() => navigate(home)}>
          <span aria-hidden="true">←</span> Back
        </button>
        <section className="cart-page" aria-labelledby="cart-title">
          <h1 id="cart-title">Your cart</h1>
          {baskets.length > 0 && (
            <div className="cart-store-labels" aria-label="Grocery baskets">
              {baskets.map((basket) => (
                <button
                  key={basket.id}
                  type="button"
                  className={`store-chip ${storeFontClass(basket.id)} ${basketStatus(basket) === "open" ? "is-open" : "is-closed"} ${basket.id === visibleId ? "is-selected" : ""}`}
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
            <section className="cart-basket" aria-label={`${visibleBasket.name} basket`}>
              {basketStatus(visibleBasket) !== "open" && (
                <p className="cart-store-warning" role="status">
                  {visibleBasket.name} is currently {basketStatus(visibleBasket)}. Its items will remain in your cart.
                </p>
              )}
              <details className="cart-items-panel" open>
                <summary>
                  <span>Items from {visibleBasket.name}</span>
                  <span>{itemCount} {itemCount === 1 ? "item" : "items"}</span>
                </summary>
                <div className="cart-lines-scroll">
                  <ul className="cart-lines">
                    {visibleBasket.lines.map((line) => {
                      const image = productImage(line);
                      return (
                        <li key={line.id} className="cart-line">
                          {image && <img className="cart-product-image" src={image} alt="" />}
                          <div className="cart-line-copy">
                            <p className="product-name">{line.name} (x{line.quantity})</p>
                            <p className="product-price">{formatMoney(line.priceCents * line.quantity)}</p>
                          </div>
                          <div className="cart-line-actions">
                            <div className="qty-control">
                              <button
                                type="button"
                                aria-label={`Decrease ${line.name}`}
                                onClick={() => changeQuantity(visibleBasket.id, line.id, line.quantity - 1)}
                              >
                                −
                              </button>
                              <span>{line.quantity}</span>
                              <button
                                type="button"
                                aria-label={`Increase ${line.name}`}
                                onClick={() => changeQuantity(visibleBasket.id, line.id, line.quantity + 1)}
                              >
                                +
                              </button>
                            </div>
                            <button
                              type="button"
                              className="cart-remove"
                              onClick={() => removeLine(visibleBasket.id, line.id)}
                            >
                              Remove
                            </button>
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              </details>
              <p className="cart-total">
                Store subtotal <strong>{formatMoney(storeSubtotal)}</strong>
              </p>
            </section>
          )}
          <div className="cart-actions cart-primary-actions">
            <button type="button" className="cart-add" onClick={() => navigate(home)}>
              + Add products
            </button>
            <button
              type="button"
              className="button-primary cart-checkout"
              disabled={baskets.length === 0}
              onClick={() => navigate(`${home}/checkout`)}
            >
              Checkout
            </button>
          </div>
        </section>
      </main>
    </div>
  );
}
