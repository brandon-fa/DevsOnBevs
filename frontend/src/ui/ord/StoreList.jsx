import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate, useParams } from "react-router-dom";
import StoreProducts from "./StoreProducts.jsx";
import { loadStores } from "./orderApi.js";
import { useCart } from "./cartContext.js";
import { dashboardPathForRole } from "../reg/accountFactory.js";
import { storeFontClass } from "./storeFonts.js";
import "./shopping.css";

const STORE_CUISINE = {
  "olive-rye": "Greek",
  "island-pantry": "Caribbean",
  "spice-route": "Indian",
  "cedar-basket": "Lebanese",
  "pearl-market": "Chinese",
};

function linesForStore(baskets, storeId) {
  for (const basket of baskets) {
    if (basket.id === storeId) return basket.lines;
  }
  return [];
}

function upsertLine(baskets, store, product) {
  const nextLine = {
    id: product.id,
    name: product.name,
    priceCents: product.priceCents,
    quantity: 1,
  };
  let foundStore = false;
  const nextBaskets = baskets.map((basket) => {
    if (basket.id !== store.id) return basket;
    foundStore = true;
    let foundLine = false;
    const lines = basket.lines.map((line) => {
      if (line.id !== product.id) return line;
      foundLine = true;
      return { ...line, quantity: line.quantity + 1 };
    });
    if (!foundLine) lines.push(nextLine);
    return { ...basket, open: store.open, lines };
  });
  if (!foundStore) {
    nextBaskets.push({
      id: store.id,
      name: store.name,
      open: store.open,
      lines: [nextLine],
    });
  }
  return nextBaskets;
}

function storeMark(name) {
  const letters = name
    .split(/\s+/)
    .filter((word) => word.length > 0)
    .slice(0, 2)
    .map((word) => word[0].toLocaleUpperCase());
  return letters.join("") || "•";
}

export default function StoreList({ onFeedback }) {
  const navigate = useNavigate();
  const { role } = useParams();
  const { baskets, setBaskets } = useCart();
  const [stores, setStores] = useState([]);
  const [selectedStore, setSelectedStore] = useState(null);
  const [loadError, setLoadError] = useState("");
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const [toast, setToast] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    async function getStores() {
      setLoading(true);
      setLoadError("");
      try {
        const loadedStores = await loadStores(controller.signal);
        if (!controller.signal.aborted) {
          setStores(loadedStores);
          if (reload > 0) onFeedback("Grocery stores refreshed.", "success");
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          const message = error.message || "Stores could not be loaded. Please try again.";
          setLoadError(message);
          onFeedback(message, "error");
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    getStores();
    return () => controller.abort();
  }, [reload, onFeedback]);

  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(null), 3000);
    return () => window.clearTimeout(timer);
  }, [toast]);

  function flashToast(text, viewCart = false) {
    setToast(null);
    window.setTimeout(() => {
      setToast({ id: Date.now(), text, viewCart });
    }, 120);
  }

  function showFeedback(text, type = "error") {
    onFeedback(text, type);
  }

  const activeLines = linesForStore(baskets, selectedStore?.id);

  function chooseStore(nextStore) {
    setSelectedStore(nextStore);
    let message = "Viewing products for " + nextStore.name + ".";
    if (!nextStore.open) message += " This store is closed.";
    const saved = linesForStore(baskets, nextStore.id);
    if (saved.length > 0) message += " Your saved products for this grocery are still in the cart.";
    showFeedback(message, "info");
  }

  function addToCart(product) {
    if (!selectedStore) {
      showFeedback("Choose a grocery store before adding products.");
      return;
    }
    if (!selectedStore.open) {
      showFeedback(`${selectedStore.name} is closed. Choose an open grocery store to add products.`);
      return;
    }
    if (product.soldOut) {
      showFeedback(`${product.name} is sold out and cannot be added to your cart. Choose another product.`);
      return;
    }
    let nextQuantity = 1;
    for (const line of activeLines) {
      if (line.id === product.id) nextQuantity = line.quantity + 1;
    }
    setBaskets((previous) => upsertLine(previous, selectedStore, product));
    flashToast(`${product.name} (x${nextQuantity}) was added to the cart.`, true);
  }

  return (
    <section className="dash-section" aria-labelledby="nearby-title">
      <div className="section-heading">
        <h2 id="nearby-title" className={selectedStore ? storeFontClass(selectedStore.id) : undefined}>
          {selectedStore ? selectedStore.name : "Nearby groceries"}
        </h2>
        <p>
          {selectedStore
            ? "Products from this market."
            : "Markets stocking the foods people cook at home."}
        </p>
      </div>
      {selectedStore ? (
        <>
          <button type="button" className="store-back" onClick={() => setSelectedStore(null)}>
            <span aria-hidden="true">←</span> All groceries
          </button>
          <StoreProducts store={{ ...selectedStore, cuisine: STORE_CUISINE[selectedStore.id], fontClass: storeFontClass(selectedStore.id) }} cart={activeLines} onAdd={addToCart} />
        </>
      ) : (
        <>
          {loading && <p role="status">Loading stores…</p>}
          {loadError && (
            <button type="button" className="retry-stores" onClick={() => setReload((value) => value + 1)}>
              Retry loading stores
            </button>
          )}
          {!loading && !loadError && stores.length === 0 && <p>No grocery stores are available right now.</p>}
          <ul className="store-grid">
            {stores.map((store) => (
              <li key={store.id}>
                <button
                  type="button"
                  className="store-card"
                  onClick={() => chooseStore(store)}
                >
                  <span className="store-card-inner">
                    <span className={`store-mark ${storeFontClass(store.id) || ""}`} aria-hidden="true">{storeMark(store.name)}</span>
                    <span className={`store-name ${storeFontClass(store.id) || ""}`}>{store.name}</span>
                    {STORE_CUISINE[store.id] && <span className="store-cuisine">{STORE_CUISINE[store.id]}</span>}
                    <span className={`store-open ${store.open ? "is-open" : "is-closed"}`}>
                      {store.open ? "Open" : "Closed"}
                    </span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
      {toast && createPortal(
        <div key={toast.id} className="cart-toast" role="status">
          <p>{toast.text}</p>
          {toast.viewCart && (
            <button
              type="button"
              className="cart-toast-action"
              onClick={() => navigate(`${dashboardPathForRole(role)}/cart`)}
            >
              View cart
            </button>
          )}
        </div>,
        document.body,
      )}
    </section>
  );
}
