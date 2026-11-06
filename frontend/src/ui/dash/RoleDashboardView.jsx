import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { storeFontClass } from "../ord/storeFonts.js";
import "../ord/shopping.css";
import "../ord/store-orders.css";

const VIEW_LABELS = {
  groceries: "Groceries View",
  courier: "Courier View",
  operator: "Operator View",
};

export default function RoleDashboardView({ role, onFeedback }) {
  if (role === "courier") {
    return <CourierOffers onFeedback={onFeedback} />;
  }
  return (
    <section className="role-empty-view" aria-label={VIEW_LABELS[role]}>
      <h1>{VIEW_LABELS[role]}</h1>
    </section>
  );
}

function CourierOffers({ onFeedback }) {
  const [orders, setOrders] = useState(null);
  const [loadError, setLoadError] = useState("");
  const [pendingId, setPendingId] = useState(null);
  const [notice, setNotice] = useState(null);

  useEffect(() => {
    if (!notice) return undefined;
    const timer = setTimeout(() => setNotice(null), 3500);
    return () => clearTimeout(timer);
  }, [notice]);

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;
    let timer;
    async function load() {
      try {
        const response = await fetch("/api/courier/orders", { credentials: "include", signal: controller.signal });
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body.error || "Deliveries could not be loaded.");
        if (!cancelled) {
          setOrders((body.orders || []).filter((order) => order.status !== "delivered"));
          setLoadError("");
        }
      } catch (error) {
        if (!cancelled && error.name !== "AbortError") {
          setLoadError(error.message || "Deliveries could not be loaded.");
        }
      } finally {
        if (!cancelled) timer = setTimeout(load, 5000);
      }
    }
    load();
    return () => {
      cancelled = true;
      controller.abort();
      clearTimeout(timer);
    };
  }, []);

  function actionFor(order) {
    if (order.status === "picked_up") return { action: "deliver", label: "Delivered", pending: "Saving…" };
    if (order.courierAccepted) return { action: "pickup", label: "Picked up", pending: "Saving…" };
    return { action: "accept", label: "Accept", pending: "Accepting…" };
  }

  async function accept(orderId, action) {
    setPendingId(orderId);
    try {
      const response = await fetch("/api/courier/orders/" + orderId + "/" + action, {
        method: "POST",
        credentials: "include",
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || "The delivery could not be accepted.");
      setOrders((body.orders || []).filter((order) => order.status !== "delivered"));
      const message = action === "pickup"
        ? "Order " + orderId + " picked up."
        : action === "deliver"
          ? "Order " + orderId + " delivered."
          : "Order " + orderId + " accepted.";
      setNotice({ id: Date.now(), text: message });
      if (onFeedback) onFeedback(message, "success");
    } catch (error) {
      if (onFeedback) onFeedback(error.message || "The delivery could not be accepted.", "error");
    } finally {
      setPendingId(null);
    }
  }

  return (
    <section className="dash-section" aria-label="Courier View">
      <div className="store-orders-heading">
        <h1>Courier View</h1>
        <p>Deliveries offered to you while you are the nearest idle courier.</p>
      </div>
      {loadError && <p className="form-alert" role="alert">{loadError}</p>}
      {orders && orders.length === 0 && <p>No deliveries are waiting for you.</p>}
      {orders && orders.length > 0 && (
        <ul className="store-order-list">
          {orders.map((order) => (
            <li key={order.orderId} className="store-order-card">
              <p className="section-kicker">Order {order.orderId}</p>
              <p className={`store-name ${storeFontClass(order.storeId)}`}>{order.storeName}</p>
              <p>Deliver to {order.destination}</p>
              <p className="section-kicker">{order.status === "picked_up" ? "Picked up" : "Ready for pickup"}</p>
              <ul className="store-order-products">
                {(order.lines || []).map((line) => (
                  <li key={line.productId}>
                    <span>{line.name}</span>
                    <span>{line.quantity}</span>
                  </li>
                ))}
              </ul>
              <button
                className={`store-order-button${actionFor(order).action === "pickup" ? " is-pickup" : ""}`}
                type="button"
                disabled={pendingId === order.orderId}
                onClick={() => accept(order.orderId, actionFor(order).action)}
              >
                {pendingId === order.orderId ? actionFor(order).pending : actionFor(order).label}
              </button>
            </li>
          ))}
        </ul>
      )}
      {notice && createPortal(
        <div key={notice.id} className="cart-toast" role="status">
          <p>{notice.text}</p>
        </div>,
        document.body,
      )}
    </section>
  );
}
