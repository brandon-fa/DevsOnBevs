import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { storeFontClass } from "../ord/storeFonts.js";
import "../ord/shopping.css";
import "../ord/store-orders.css";

const VIEW_LABELS = {
  groceries: "Groceries View",
  courier: "Courier View",
};

export default function RoleDashboardView({ role, onFeedback }) {
  if (role === "courier") {
    return <CourierOffers onFeedback={onFeedback} />;
  }
  if (role === "operator") {
    return <OperatorCouriers onFeedback={onFeedback} />;
  }
  return (
    <section className="role-empty-view" aria-label={VIEW_LABELS[role]}>
      <h1>{VIEW_LABELS[role]}</h1>
    </section>
  );
}

function statusLabel(order) {
  if (order.status === "picked_up") return "Picked up";
  if (order.courierAccepted || order.status === "accepted") return "Accepted";
  return "Offered";
}

function OperatorCouriers({ onFeedback }) {
  const [couriers, setCouriers] = useState(null);
  const [loadError, setLoadError] = useState("");
  const [pendingId, setPendingId] = useState(null);
  const [choices, setChoices] = useState({});
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
        const response = await fetch("/api/operator/couriers", { credentials: "include", signal: controller.signal });
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body.error || "Couriers could not be loaded.");
        if (!cancelled) {
          setCouriers(body.couriers || []);
          setLoadError("");
        }
      } catch (error) {
        if (!cancelled && error.name !== "AbortError") {
          setLoadError(error.message || "Couriers could not be loaded.");
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

  async function reassign(orderId) {
    const courierUserId = Number(choices[orderId]);
    if (!Number.isInteger(courierUserId) || courierUserId <= 0) {
      if (onFeedback) onFeedback("Choose a courier to receive this order.", "error");
      return;
    }
    const courierName = (couriers || []).find((courier) => courier.userId === courierUserId)?.name || "the selected courier";
    setPendingId(orderId);
    try {
      const response = await fetch("/api/operator/couriers/orders/" + orderId + "/reassign", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ courierUserId }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || "The order could not be reassigned.");
      setCouriers(body.couriers || []);
      setChoices((current) => ({ ...current, [orderId]: "" }));
      const message = "Order " + orderId + " reassigned to " + courierName + ".";
      setNotice({ id: Date.now(), text: message });
      if (onFeedback) onFeedback(message, "success");
    } catch (error) {
      if (onFeedback) onFeedback(error.message || "The order could not be reassigned.", "error");
    } finally {
      setPendingId(null);
    }
  }

  return (
    <section className="dash-section" aria-label="Operator View">
      <div className="store-orders-heading">
        <h1>Operator View</h1>
        <p>Courier deliveries across all grocery stores. Only you can reassign an accepted order.</p>
      </div>
      {loadError && <p className="form-alert" role="alert">{loadError}</p>}
      {couriers && couriers.length === 0 && <p>No couriers are registered.</p>}
      {couriers && couriers.length > 0 && (
        <ul className="store-order-list operator-courier-list">
          {couriers.map((courier) => (
            <li key={courier.userId} className="store-order-card">
              <p className="section-kicker">{courier.status}</p>
              <p className="store-name">{courier.name}</p>
              {(courier.orders || []).length === 0 && <p>No orders.</p>}
              {(courier.orders || []).length > 0 && (
                <ul className="operator-order-list">
                  {(courier.orders || []).map((order) => {
                    const accepted = order.courierAccepted || order.status === "accepted";
                    const canReassign = accepted && order.status !== "picked_up" && order.status !== "delivered";
                    return (
                      <li key={order.orderId}>
                        <p>Order {order.orderId} · {order.storeName}</p>
                        <p>Deliver to {order.destination}</p>
                        <p className="section-kicker">{statusLabel(order)}</p>
                        {canReassign && (
                          <div className="operator-reassign">
                            <label htmlFor={"reassign-" + order.orderId}>Reassign to</label>
                            <select
                              id={"reassign-" + order.orderId}
                              value={choices[order.orderId] || ""}
                              onChange={(event) => setChoices((current) => ({ ...current, [order.orderId]: event.target.value }))}
                            >
                              <option value="">Choose a courier</option>
                              {couriers.filter((candidate) => candidate.userId !== courier.userId).map((candidate) => (
                                <option key={candidate.userId} value={candidate.userId}>{candidate.name}</option>
                              ))}
                            </select>
                            <button
                              className="store-order-button"
                              type="button"
                              disabled={pendingId === order.orderId}
                              onClick={() => reassign(order.orderId)}
                            >
                              {pendingId === order.orderId ? "Reassigning…" : "Reassign"}
                            </button>
                          </div>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
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

function CourierOffers({ onFeedback }) {
  const [orders, setOrders] = useState(null);
  const [courierStatus, setCourierStatus] = useState(null);
  const [loadError, setLoadError] = useState("");
  const [pendingId, setPendingId] = useState(null);
  const [availabilityPending, setAvailabilityPending] = useState(false);
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
          setCourierStatus(body.status || null);
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

  const carryingOrder = (orders || []).some((order) => order.status === "picked_up");

  async function toggleAvailability() {
    const online = courierStatus === "offline";
    setAvailabilityPending(true);
    try {
      const response = await fetch("/api/courier/status", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ online }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || "Courier availability could not be updated.");
      setCourierStatus(body.status);
      if (onFeedback) onFeedback(online ? "You are now online." : "You are now offline.", "success");
    } catch (error) {
      if (onFeedback) onFeedback(error.message || "Courier availability could not be updated.", "error");
    } finally {
      setAvailabilityPending(false);
    }
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
      setCourierStatus(body.status || courierStatus);
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
        <p>Availability: {courierStatus || "loading"}</p>
        <button
          className="store-order-button"
          type="button"
          disabled={availabilityPending || (courierStatus !== "offline" && carryingOrder)}
          onClick={toggleAvailability}
        >
          {availabilityPending ? "Updating…" : courierStatus === "offline" ? "Go online" : "Go offline"}
        </button>
        {carryingOrder && <p>Deliver your current order before going offline.</p>}
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
