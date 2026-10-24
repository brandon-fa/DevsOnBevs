import { useEffect, useRef, useState } from "react";
import { loadStoreOrders, updateStoreOrder } from "./orderApi.js";
import { formatMoney } from "../../data/mockData.js";
import "./store-orders.css";

const ORDER_SECTIONS = [
    { name: "pending", title: "Pending orders", empty: "No pending orders." },
    { name: "preparing", title: "Preparing orders", empty: "The preparation queue is empty." },
    { name: "ready", title: "Ready orders", empty: "No orders are ready for pickup." },
];

function queueLabel(index) {
    if (index === 0) return "Next to prepare";
    return "Queue position " + (index + 1);
}

export default function StoreOrders({ onFeedback }) {
    const [orders, setOrders] = useState(null);
    const [loadError, setLoadError] = useState("");
    const [updating, setUpdating] = useState(false);
    const [refresh, setRefresh] = useState(0);
    const [rejectionReasons, setRejectionReasons] = useState({});
    const lastLoadError = useRef("");
    const refreshRequested = useRef(false);

    useEffect(() => {
        if (updating) return;
        const controller = new AbortController();
        let cancelled = false;
        let timer;
        async function load() {
            const reportRefresh = refreshRequested.current;
            refreshRequested.current = false;
            try {
                const data = await loadStoreOrders(controller.signal);
                if (!cancelled) {
                    setOrders(data);
                    setLoadError("");
                    lastLoadError.current = "";
                    if (reportRefresh) onFeedback(`Orders refreshed for ${data.storeName}.`, "success");
                }
            } catch (error) {
                if (!cancelled) {
                    const message = error.message || "Store orders could not be loaded.";
                    setLoadError(message);
                    if (reportRefresh || lastLoadError.current !== message) onFeedback(message, "error");
                    lastLoadError.current = message;
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
    }, [refresh, updating, onFeedback]);

    async function act(orderId, action, reason = "") {
        if (updating) return;
        if (action === "reject" && !reason.trim()) {
            onFeedback("Enter a reason for rejecting the order.", "error");
            return;
        }
        if (action === "ready") {
            const nextOrder = orders.preparing[0];
            if (!nextOrder || nextOrder.orderId !== orderId) {
                onFeedback("Only the next order in the preparation queue can be marked ready.", "error");
                return;
            }
        }
        setUpdating(true);
        try {
            const data = await updateStoreOrder(orderId, action, reason);
            setOrders(data);
            let message = "Order " + orderId;
            if (action === "accept") message += " added to the preparation queue.";
            if (action === "ready") message += " marked ready.";
            if (action === "reject") message += " rejected. Reason: " + reason.trim();
            onFeedback(message, "success");
            setRejectionReasons((previous) => {
                const remaining = { ...previous };
                delete remaining[orderId];
                return remaining;
            });
        } catch (error) {
            onFeedback(error.message || "The order could not be updated. Refresh before trying again.", "error");
        } finally {
            setUpdating(false);
        }
    }

    function refreshOrders() {
        refreshRequested.current = true;
        setRefresh((value) => value + 1);
    }

    function changeRejectionReason(orderId, value) {
        setRejectionReasons((previous) => {
            const reasons = { ...previous };
            reasons[orderId] = value;
            return reasons;
        });
    }

    return (
        <section className="store-orders dash-section" aria-labelledby="store-orders-title">
            <div className="store-orders-heading">
                <div>
                    <h1 id="store-orders-title">{orders?.storeName || "Your grocery store"}</h1>
                    <p>Accept pending orders or reject them with a reason. Prepare accepted orders in queue order.</p>
                </div>
                <button type="button" className="store-order-button" disabled={updating}
                    onClick={refreshOrders}>Refresh orders</button>
            </div>
            {!orders && !loadError && <p role="status">Loading store orders…</p>}
            {orders && (
                <div className="store-order-columns" aria-busy={updating}>
                    {ORDER_SECTIONS.map((section) => (
                        <section key={section.name} className="store-order-column" aria-labelledby={"orders-" + section.name}>
                            <h2 id={"orders-" + section.name}>{section.title} ({orders[section.name].length})</h2>
                            {orders[section.name].length === 0 && <p>{section.empty}</p>}
                            <ol className="store-order-list">
                                {orders[section.name].map((order, index) => (
                                    <li key={order.orderId} className="store-order-card">
                                        <h3>Order {order.orderId}</h3>
                                        {section.name === "preparing" && <p className="section-kicker">
                                            {queueLabel(index)}
                                        </p>}
                                        <p>Deliver to: {order.destination}</p>
                                        <ul className="store-order-products">
                                            {order.lines.map((line) => (
                                                <li key={line.productId}>
                                                    <span>{line.quantity} × {line.name}</span>
                                                    <span className="money">{formatMoney(line.priceCents * line.quantity)}</span>
                                                </li>
                                            ))}
                                        </ul>
                                        {section.name === "pending" && <>
                                            <button type="button" className="store-order-button"
                                                disabled={updating} onClick={() => act(order.orderId, "accept")}>Accept order</button>
                                            <form className="store-order-rejection" noValidate onSubmit={(event) => {
                                                event.preventDefault();
                                                act(order.orderId, "reject", rejectionReasons[order.orderId] || "");
                                            }}>
                                                <label htmlFor={`rejection-reason-${order.orderId}`}>Reason for rejection</label>
                                                <textarea id={`rejection-reason-${order.orderId}`} name="reason" rows={2} required
                                                    disabled={updating} value={rejectionReasons[order.orderId] || ""}
                                                    onChange={(event) => changeRejectionReason(order.orderId, event.target.value)} />
                                                <button type="submit" className="store-order-button store-order-button-secondary"
                                                    disabled={updating}>Reject order</button>
                                            </form>
                                        </>}
                                        {section.name === "preparing" && <button type="button" className="store-order-button"
                                            disabled={updating} aria-disabled={updating || index !== 0}
                                            onClick={() => act(order.orderId, "ready")}>Mark ready</button>}
                                        {section.name === "ready" && <p className="section-kicker">Awaiting pickup</p>}
                                    </li>
                                ))}
                            </ol>
                        </section>
                    ))}
                </div>
            )}
        </section>
    );
}
