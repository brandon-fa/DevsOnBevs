import { useEffect, useRef, useState } from "react";
import { loadCustomerOrders } from "./orderApi.js";
import { formatMoney } from "../../data/mockData.js";
import "./customer-orders.css";

const STATUS_LABELS = {
  open: "Open",
  placed: "Placed",
  preparing: "Preparing",
  ready: "Ready for pickup",
  canceled: "Canceled",
};
const STEPS = [
  { status: "placed", label: "Placed" },
  { status: "preparing", label: "Preparing" },
  { status: "ready", label: "Ready for pickup" },
];

function statusLabel(status, reason) {
  if (status === "canceled" && reason) return "Rejected";
  return STATUS_LABELS[status] || status;
}

function findOrder(orders, orderId) {
  for (const order of orders) {
    if (order.orderId === orderId) return order;
  }
  return null;
}

function chooseOrder(orders, selectedId) {
  const selected = findOrder(orders, selectedId);
  if (selected) return selected;

  for (const order of orders) {
    if (order.status !== "canceled") return order;
  }
  if (orders.length > 0) return orders[0];
  return null;
}

function addNewOrder(orders, newOrder) {
  const result = [newOrder];
  for (const order of orders) {
    if (order.orderId !== newOrder.orderId) result.push(order);
  }
  return result;
}

export default function CustomerOrders({ latestOrder, onSelectOrder, onFeedback }) {
  const [orders, setOrders] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [refresh, setRefresh] = useState(0);

  // Remember the last response so background refreshes do not repeat console messages.
  const previousOrders = useRef(null);
  const lastError = useRef("");
  const refreshRequested = useRef(false);

  let latestOrderId = null;
  if (latestOrder) latestOrderId = latestOrder.orderId;

  useEffect(() => {
    if (!latestOrder) return;
    setOrders((previous) => addNewOrder(previous, latestOrder));
    setSelectedId(latestOrder.orderId);
    setLoading(false);
    setLoadFailed(false);
    if (previousOrders.current !== null) {
      previousOrders.current = addNewOrder(previousOrders.current, latestOrder);
    }
  }, [latestOrder]);

  useEffect(() => {
    const controller = new AbortController();
    let stopped = false;
    let timer;

    async function load() {
      const showRefreshMessage = refreshRequested.current;
      refreshRequested.current = false;
      try {
        const received = await loadCustomerOrders(controller.signal);
        if (stopped) return;
        const sorted = received.slice().sort((first, second) => second.orderId - first.orderId);

        if (previousOrders.current !== null) {
          for (const order of sorted) {
            const previous = findOrder(previousOrders.current, order.orderId);
            const changed = !previous || previous.status !== order.status
              || previous.rejectionReason !== order.rejectionReason;
            if (changed) {
              let message = "Order " + order.orderId + ": " + statusLabel(order.status, order.rejectionReason) + ".";
              let type = "info";
              if (order.rejectionReason) message += " Reason: " + order.rejectionReason;
              if (order.status === "canceled") type = "error";
              onFeedback(message, type);
            }
          }
        }

        previousOrders.current = sorted;
        lastError.current = "";
        setOrders(sorted);
        setSelectedId((previousId) => {
          const selected = chooseOrder(sorted, previousId);
          if (selected) return selected.orderId;
          return null;
        });
        setLoadFailed(false);
        if (showRefreshMessage) onFeedback("Your order tracking has been refreshed.", "success");
      } catch (error) {
        if (stopped) return;
        let message = error.message || "Your orders could not be loaded.";
        if (error instanceof TypeError) message = "Your orders could not be loaded. Check your connection.";
        setLoadFailed(true);
        if (showRefreshMessage || lastError.current !== message) onFeedback(message, "error");
        lastError.current = message;
      } finally {
        if (!stopped) {
          setLoading(false);
          timer = setTimeout(load, 5000);
        }
      }
    }

    load();
    return () => {
      stopped = true;
      controller.abort();
      clearTimeout(timer);
    };
  }, [latestOrderId, refresh, onFeedback]);

  const selectedOrder = chooseOrder(orders, selectedId);
  useEffect(() => {
    onSelectOrder(selectedOrder);
  }, [selectedOrder, onSelectOrder]);

  function refreshOrders() {
    refreshRequested.current = true;
    setRefresh((value) => value + 1);
  }

  function selectOrder(event) {
    const orderId = Number(event.target.value);
    setSelectedId(orderId);
    onFeedback("Tracking order " + orderId + ".", "info");
  }

  return (
    <section className="customer-orders dash-section" aria-labelledby="customer-orders-title">
      <div className="customer-orders-heading">
        <div>
          <h2 id="customer-orders-title">Track your orders</h2>
          <p>Follow updates from your grocery store.</p>
        </div>
        <button type="button" className="tracking-refresh" onClick={refreshOrders}>Refresh orders</button>
      </div>
      {loading && <p>Loading your orders…</p>}
      {!loading && !loadFailed && orders.length === 0 && (
        <p className="customer-orders-empty">You have no orders yet. Choose a store and place your first order below.</p>
      )}
      {orders.length > 1 && (
        <label className="customer-orders-selector" htmlFor="tracked-order">
          Choose an order
          <select id="tracked-order" value={selectedOrder.orderId} onChange={selectOrder}>
            {orders.map((order) => (
              <option key={order.orderId} value={order.orderId}>
                Order {order.orderId} — {order.storeName} — {statusLabel(order.status, order.rejectionReason)}
              </option>
            ))}
          </select>
        </label>
      )}
      {selectedOrder && <OrderTracking order={selectedOrder} />}
    </section>
  );
}

function OrderTracking({ order }) {
  let currentStep = -1;
  for (let index = 0; index < STEPS.length; index++) {
    if (STEPS[index].status === order.status) currentStep = index;
  }

  const descriptions = {
    placed: "Your order is waiting for the grocery store to accept it.",
    preparing: "The grocery store accepted your order and added it to the preparation queue.",
    ready: "Your order has been prepared and is ready for pickup.",
    canceled: "This order was canceled.",
  };
  let description = descriptions[order.status];
  if (order.status === "canceled" && order.rejectionReason) {
    description = "The grocery store rejected your order.";
  }

  return (
    <article className="order-card customer-order-card" aria-label={"Tracking order " + order.orderId}>
      <div>
        <p className="section-kicker">Order {order.orderId} · {statusLabel(order.status, order.rejectionReason)}</p>
        <h3>{order.storeName}</h3>
        <p className="order-copy">{description}</p>
        <p className="order-copy">Deliver to: {order.destination}</p>
        {order.rejectionReason && <p className="order-copy">Reason: {order.rejectionReason}</p>}
        {currentStep >= 0 && (
          <ol className="steps" aria-label="Order progress">
            {STEPS.map((step, index) => {
              let className = "step";
              let progressLabel = "Later";
              if (index < currentStep) {
                className += " is-done";
                progressLabel = "Done";
              } else if (index === currentStep) {
                className += " is-current";
                progressLabel = "Now";
              }
              return (
                <li key={step.status} className={className} aria-current={index === currentStep ? "step" : undefined}>
                  <span className="step-label">{step.label}</span>
                  <span className="step-state">{progressLabel}</span>
                </li>
              );
            })}
          </ol>
        )}
        <details className="customer-order-history">
          <summary>Order history</summary>
          <ol>
            {order.transitionEvents.map((event, index) => (
              <li key={event.timestamp + "-" + index}>
                <time dateTime={event.timestamp}>{new Date(event.timestamp).toLocaleString()}</time>
                <span>{statusLabel(event.fromState)} → {statusLabel(event.toState, event.reason)}</span>
                {event.reason && <span>Reason: {event.reason}</span>}
              </li>
            ))}
          </ol>
        </details>
      </div>
      <div className="order-meta">
        <ul className="item-list">
          {order.lines.map((line) => (
            <li key={line.productId}>
              <span>{line.quantity} × {line.name}</span>
              <span className="money">{formatMoney(line.priceCents * line.quantity)}</span>
            </li>
          ))}
        </ul>
        <p className="total-row"><span>Subtotal</span><span className="money">{formatMoney(order.subtotalCents)}</span></p>
      </div>
    </article>
  );
}
