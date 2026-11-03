import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import StoreProducts from "./StoreProducts.jsx";
import { loadStores } from "./orderApi.js";
import { useCart } from "./cartContext.js";
import "./shopping.css";

function storeMark(name) {
  const letters = name
    .split(/\s+/)
    .filter((word) => word.length > 0)
    .slice(0, 2)
    .map((word) => word[0].toLocaleUpperCase());
  return letters.join("") || "•";
}

export default function StoreList({ onFeedback }) {
  const { cart, setCart, setStore } = useCart();
  const [stores, setStores] = useState([]);
  const [selectedStore, setSelectedStore] = useState(null);
  const [loadError, setLoadError] = useState("");
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const [toast, setToast] = useState("");

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
    const timer = window.setTimeout(() => setToast(""), 3000);
    return () => window.clearTimeout(timer);
  }, [toast]);

  function showFeedback(text, type = "error") {
    onFeedback(text, type);
  }

  function chooseStore(nextStore) {
    setSelectedStore(nextStore);
    setStore({ id: nextStore.id, name: nextStore.name, open: nextStore.open });
    setCart([]);
    let message = "Viewing products for " + nextStore.name + ".";
    if (!nextStore.open) message += " This store is closed.";
    if (cart.length > 0) message += " Your cart has been cleared.";
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
    setCart((previous) => {
      const updatedCart = [...previous];
      for (let index = 0; index < updatedCart.length; index++) {
        const line = updatedCart[index];
        if (line.id === product.id) {
          updatedCart[index] = { ...line, quantity: line.quantity + 1 };
          return updatedCart;
        }
      }
      updatedCart.push({
        id: product.id,
        name: product.name,
        priceCents: product.priceCents,
        quantity: 1,
      });
      return updatedCart;
    });
    setToast("Product was successfully added to the cart!");
  }

  return (
    <section className="dash-section" aria-labelledby="nearby-title">
      <div className="section-heading">
        <h2 id="nearby-title">Nearby groceries</h2>
        <p>Markets stocking the foods people cook at home.</p>
      </div>
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
              className={`store-card${selectedStore?.id === store.id ? " is-selected" : ""}`}
              aria-pressed={selectedStore?.id === store.id}
              onClick={() => chooseStore(store)}
            >
              <span className="store-card-inner">
                <span className="store-mark" aria-hidden="true">{storeMark(store.name)}</span>
                <span className="store-name">{store.name}</span>
                <span className={`store-open ${store.open ? "is-open" : "is-closed"}`}>
                  {store.open ? "Open" : "Closed"}
                </span>
              </span>
            </button>
          </li>
        ))}
      </ul>
      {selectedStore && (
        <StoreProducts store={selectedStore} onAdd={addToCart} />
      )}
      {toast && createPortal(
        <p className="cart-toast" role="status">{toast}</p>,
        document.body,
      )}
    </section>
  );
}
