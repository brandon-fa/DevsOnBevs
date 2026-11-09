import { useEffect, useRef, useState } from "react";
import { loadCustomerOrders } from "./orderApi.js";
import { formatMoney } from "../../data/mockData.js";
import { storeFontClass } from "./storeFonts.js";
import "./shopping.css";
import "./customer-orders.css";

const STATUS_LABELS = {
  open: "Open",
  placed: "Placed",
  preparing: "Preparing",
  ready: "Ready for pickup",
  canceled: "Canceled",
  delivered: "Delivered",
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

export default function CustomerOrders({ latestOrder, onSelectOrder, onFeedback, storeId = null }) {
  const [orders, setOrders] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [summaryId, setSummaryId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);

  // Remember the last response so background refreshes do not repeat console messages.
  const previousOrders = useRef(null);
  const lastError = useRef("");

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
      } catch (error) {
        if (stopped) return;
        let message = error.message || "Your orders could not be loaded.";
        if (error instanceof TypeError) message = "Your orders could not be loaded. Check your connection.";
        setLoadFailed(true);
        if (lastError.current !== message) onFeedback(message, "error");
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
  }, [latestOrderId, onFeedback]);

  const visibleOrders = storeId == null
    ? orders
    : orders.filter((order) => String(order.storeId) === String(storeId));
  const selectedOrder = chooseOrder(visibleOrders, selectedId);
  const summaryOrder = findOrder(visibleOrders, summaryId);
  useEffect(() => {
    if (onSelectOrder) onSelectOrder(selectedOrder);
  }, [selectedOrder, onSelectOrder]);

  function selectOrder(order) {
    setSelectedId(order.orderId);
    onFeedback("Tracking order " + order.orderId + " from " + order.storeName + ".", "info");
  }

  return (
    <section className="customer-orders dash-section" aria-labelledby="customer-orders-title">
      <div className="customer-orders-heading">
        <h2 id="customer-orders-title">Your orders</h2>
        <p>Track active orders or open the summary for a delivered order.</p>
      </div>
      {loading && <p>Loading your orders…</p>}
      {!loading && !loadFailed && visibleOrders.length === 0 && (
        <p className="customer-orders-empty">
          {storeId == null
            ? "You have no orders yet. Choose a store and place your first order."
            : "You have no orders from this restaurant yet."}
        </p>
      )}
      {visibleOrders.length > 0 && (
        <div className="customer-orders-list">
          {visibleOrders.map((order) => (
            <OrderTracking
              key={order.orderId}
              order={order}
              selected={selectedOrder?.orderId === order.orderId}
              onSelect={() => selectOrder(order)}
              onViewSummary={() => setSummaryId(order.orderId)}
            />
          ))}
        </div>
      )}
      {summaryOrder && <OrderSummary order={summaryOrder} onClose={() => setSummaryId(null)} />}
    </section>
  );
}

function OrderTracking({ order, selected, onSelect, onViewSummary }) {
  let currentStep = -1;
  for (let index = 0; index < STEPS.length; index++) {
    if (STEPS[index].status === order.status) currentStep = index;
  }

  const descriptions = {
    placed: "Your order is waiting for the grocery store to accept it.",
    preparing: "The grocery store accepted your order and added it to the preparation queue.",
    ready: "Your order has been prepared and is ready for pickup.",
    canceled: "This order was canceled.",
    delivered: "This order has been delivered. Its final order summary is available.",
  };
  let description = descriptions[order.status];
  if (order.status === "canceled" && order.rejectionReason) {
    description = "The grocery store rejected your order.";
  }

  return (
    <article
      className={`order-card customer-order-card${selected ? " is-selected" : ""}`}
      aria-label={"Tracking order " + order.orderId + " from " + order.storeName}
    >
      <div>
        <button
          type="button"
          className={`store-chip ${storeFontClass(order.storeId)} ${order.status === "canceled" ? "is-closed" : "is-open"}`}
          onClick={onSelect}
        >
          {order.storeName}
        </button>
        <p className="section-kicker">Order {order.orderId} · {statusLabel(order.status, order.rejectionReason)}</p>
        <p className="order-copy">{description}</p>
        <p className="order-copy">Deliver to: {order.destination}</p>
        {order.rejectionReason && <p className="order-copy">Reason: {order.rejectionReason}</p>}
        {order.status === "delivered" && (
          <button type="button" className="order-summary-button" onClick={onViewSummary}>
            View order summary
          </button>
        )}
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
        {order.status !== "delivered" && (
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
        )}
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

function OrderSummary({ order, onClose }) {
  return (
    <section className="order-summary-panel" aria-labelledby="order-summary-title" aria-live="polite">
      <div className="order-summary-heading">
        <div>
          <p className="section-kicker">Delivered order · {order.orderId}</p>
          <h2 id="order-summary-title">Order summary</h2>
          <p>{order.storeName} · Deliver to: {order.destination}</p>
        </div>
        <button type="button" className="order-summary-close" onClick={onClose}>Close summary</button>
      </div>
      <ul className="item-list order-summary-items">
        {order.lines.map((line) => (
          <li key={line.productId}>
            <span>{line.quantity} × {line.name}</span>
            <span className="money">{formatMoney(line.priceCents * line.quantity)}</span>
          </li>
        ))}
      </ul>
      <dl className="order-summary-costs">
        <div className="fee-row"><dt>Subtotal</dt><dd>{formatMoney(order.subtotalCents)}</dd></div>
        <div className="fee-row"><dt>Delivery fee</dt><dd>{formatMoney(order.deliveryFeeCents)}</dd></div>
        <div className="fee-row"><dt>Service fee</dt><dd>{formatMoney(order.serviceFeeCents)}</dd></div>
        {order.smallOrderSurchargeCents > 0 && (
          <div className="fee-row"><dt>Small-order surcharge</dt><dd>{formatMoney(order.smallOrderSurchargeCents)}</dd></div>
        )}
        <div className="total-row order-summary-total"><dt>Total paid</dt><dd>{formatMoney(order.totalCents)}</dd></div>
      </dl>
    </section>
  );
}
