import { useState } from "react";
import { formatMoney } from "../../data/mockData.js";
import { setStoreOpen } from "../ord/orderApi.js";
import { storeFontClass } from "../ord/storeFonts.js";
import ResetControl from "./ResetControl.jsx";
import useOperatorStores from "./useOperatorStores.js";
import "../ord/shopping.css";
import "../ord/store-orders.css";
import "./operator.css";

// The database statuses behind each column. Delivered and cancelled orders are not current.
const ORDER_STAGES = [
  { id: "placed", statuses: ["placed"], title: "Placed", kicker: "Awaiting store", empty: "No orders are waiting on the store." },
  { id: "preparing", statuses: ["accepted", "preparing"], title: "Preparing", kicker: "In preparation", empty: "The preparation queue is empty." },
  { id: "ready", statuses: ["ready"], title: "Ready for pickup", kicker: "Awaiting pickup", empty: "No orders are waiting for a courier." },
  { id: "transit", statuses: ["picked_up", "on_the_way"], title: "In transit", kicker: "Out for delivery", empty: "No orders are out for delivery." },
];

const STORE_STATUS_LABELS = {
  open: "Open",
  paused: "Paused",
  closed: "Closed",
};

const QUEUE_BAND_LABELS = {
  short: "Short",
  building: "Building",
  long: "Long",
};

export default function OperatorDashboard({ onFeedback }) {
  const { stores, error, updateStore } = useOperatorStores(onFeedback);
  const [selectedStoreId, setSelectedStoreId] = useState(null);
  const selectedStore = stores?.find((store) => store.id === selectedStoreId);

  if (selectedStore) {
    return (
      <StoreCurrentOrders
        store={selectedStore}
        onBack={() => setSelectedStoreId(null)}
        onStoreChange={(changes) => updateStore(selectedStore.id, changes)}
        onFeedback={onFeedback}
      />
    );
  }

  return (
    <>
      <section className="dash-section" aria-labelledby="operator-stores-title">
        <div className="section-heading">
          <h2 id="operator-stores-title">Grocery stores</h2>
          <p>Select a store to see its current orders.</p>
        </div>
        {!stores && !error && <p role="status">Loading stores…</p>}
        {error && <p role="alert">{error}</p>}
        {stores?.length === 0 && <p>No grocery stores are available.</p>}
        {stores?.length > 0 && (
          <ul className="operator-panel operator-store-list">
            {stores.map((store) => (
              <li key={store.id}>
                <button type="button" className="operator-store-row" onClick={() => setSelectedStoreId(store.id)}>
                  <span className={`store-name ${storeFontClass(store.id)}`}>{store.name}</span>
                  <StoreStatus status={store.status} />
                  <QueueStatus band={store.queueBand} queued={store.queued} />
                  <span className="operator-store-arrow" aria-hidden="true">→</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
      <ResetControl onFeedback={onFeedback} />
    </>
  );
}

function StoreCurrentOrders({ store, onBack, onStoreChange, onFeedback }) {
  const [updating, setUpdating] = useState(false);
  const [updateError, setUpdateError] = useState("");
  const action = store.open
    ? { verb: "close", label: "Close store", busy: "Closing…" }
    : { verb: "open", label: "Open store", busy: "Opening…" };

  async function toggleOpen() {
    setUpdating(true);
    setUpdateError("");
    onFeedback(`Operator command: ${action.verb} ${store.name}.`, "info");
    try {
      const changes = await setStoreOpen(store.id, !store.open);
      onStoreChange(changes);
      onFeedback(`${store.name} is now ${changes.status}.`, "success");
    } catch (error) {
      let message = error.message || `${store.name} could not be updated.`;
      if (error instanceof TypeError) message = `${store.name} could not be updated. Check your connection.`;
      setUpdateError(message);
      onFeedback(message, "error");
    } finally {
      setUpdating(false);
    }
  }

  return (
    <section className="dash-section" aria-labelledby="operator-store-title">
      <button type="button" className="store-back" onClick={onBack}>
        <span aria-hidden="true">←</span> All stores
      </button>
      <div className="operator-store-header">
        <div>
          <h2 id="operator-store-title" className={storeFontClass(store.id)}>{store.name}</h2>
          <p className="operator-store-facts">
            <StoreStatus status={store.status} />
            <QueueStatus band={store.queueBand} queued={store.queued} />
          </p>
        </div>
        <button type="button" className="store-order-button" disabled={updating} onClick={toggleOpen}>
          {updating ? action.busy : action.label}
        </button>
      </div>
      {updateError && <p role="alert" className="operator-store-error">{updateError}</p>}
      <div className="operator-order-columns">
        {ORDER_STAGES.map((stage) => {
          const orders = store.orders.filter((order) => stage.statuses.includes(order.status));
          return (
            <section key={stage.id} className="store-order-column" aria-labelledby={`operator-stage-${stage.id}`}>
              <h3 id={`operator-stage-${stage.id}`}>{stage.title} ({orders.length})</h3>
              {orders.length === 0 && <p>{stage.empty}</p>}
              <ol className="store-order-list">
                {orders.map((order) => (
                  <li key={order.orderId} className="store-order-card">
                    <h4 className="operator-order-title">Order {order.orderId}</h4>
                    <p className="section-kicker">{stage.kicker}</p>
                    <p>{order.customer}</p>
                    <p>Deliver to: {order.destination}</p>
                    <p>
                      {order.itemCount} {order.itemCount === 1 ? "item" : "items"} · <span className="money">{formatMoney(order.totalCents)}</span>
                    </p>
                    {(stage.id === "ready" || stage.id === "transit") && (
                      <p>Courier: {order.courier || "Not assigned"}</p>
                    )}
                    {stage.id === "ready" && (
                      <button type="button" className="store-order-button" aria-label={`Reassign courier for order ${order.orderId}`}>
                        Reassign courier
                      </button>
                    )}
                  </li>
                ))}
              </ol>
            </section>
          );
        })}
      </div>
    </section>
  );
}

function StoreStatus({ status }) {
  return <span className={`store-chip is-${status}`}>{STORE_STATUS_LABELS[status]}</span>;
}

function QueueStatus({ band, queued }) {
  return (
    <span className="operator-queue">
      <span className={`operator-queue-dot is-${band}`} aria-hidden="true" />
      {QUEUE_BAND_LABELS[band]} · {queued} queued
    </span>
  );
}
