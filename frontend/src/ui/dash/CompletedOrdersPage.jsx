import { Navigate, useNavigate, useParams } from "react-router-dom";
import { formatMoney } from "../../data/mockData.js";
import { dashboardPathForRole } from "../reg/accountFactory.js";
import { storeFontClass } from "../ord/storeFonts.js";
import { appendConsoleMessage } from "./consoleFeed.js";
import SiteHeader from "./SiteHeader.jsx";
import useOperatorStores from "./useOperatorStores.js";
import useSessionAccount from "./useSessionAccount.js";
import "./dashboard.css";
import "../ord/shopping.css";
import "../ord/store-orders.css";
import "./operator.css";

const DATE_FORMAT = { year: "numeric", month: "long", day: "numeric" };
const TIME_FORMAT = { hour: "numeric", minute: "2-digit" };

// Delivered orders grouped by the local day they were placed, newest day and order first.
function completedDays(orders) {
  const days = new Map();
  const newestFirst = orders
    .filter((order) => order.status === "delivered")
    .sort((first, second) => second.createdAt.localeCompare(first.createdAt));
  for (const order of newestFirst) {
    const placedAt = new Date(order.createdAt);
    const date = placedAt.toLocaleDateString("en-CA", DATE_FORMAT);
    if (!days.has(date)) days.set(date, []);
    days.get(date).push({ ...order, time: placedAt.toLocaleTimeString("en-CA", TIME_FORMAT) });
  }
  return Array.from(days, ([date, dayOrders]) => ({ date, orders: dayOrders }));
}

export default function CompletedOrdersPage() {
  const navigate = useNavigate();
  const { role: routeRole } = useParams();
  const { account, loading } = useSessionAccount();

  if (loading) {
    return <main className="dash-main" aria-live="polite">Loading completed orders…</main>;
  }
  if (!account) return <Navigate to="/login" replace />;
  if (account.role !== "operator") return <Navigate to={dashboardPathForRole(account.role)} replace />;
  if (routeRole !== account.role) return <Navigate to={`${dashboardPathForRole(account.role)}/completed-orders`} replace />;

  return (
    <div className="dashboard">
      <a className="skip-link" href="#main">Skip to content</a>
      <SiteHeader account={account} />
      <main id="main" className="dash-main">
        <button className="back-link" type="button" onClick={() => navigate(dashboardPathForRole(account.role))}>
          <span aria-hidden="true">←</span> Back
        </button>
        <div className="dash-intro dash-section">
          <h1>Completed orders</h1>
          <p className="lede">Every completed order, by store and then by the date it was placed.</p>
        </div>
        <CompletedOrders />
      </main>
    </div>
  );
}

function CompletedOrders() {
  const { stores, error } = useOperatorStores(appendConsoleMessage);

  if (error) return <p role="alert">{error}</p>;
  if (!stores) return <p role="status">Loading completed orders…</p>;
  if (stores.length === 0) return <p>No grocery stores are available.</p>;

  return stores.map((store) => {
    const days = completedDays(store.orders);
    return (
      <section key={store.id} className="dash-section" aria-labelledby={`completed-${store.id}`}>
        <h2 id={`completed-${store.id}`} className={`operator-completed-store ${storeFontClass(store.id)}`}>
          {store.name}
        </h2>
        <div className="operator-panel">
          {days.length === 0 && <p className="operator-completed-empty">No completed orders yet.</p>}
          {days.map((day) => (
            <section key={day.date} className="operator-completed-day" aria-label={`${store.name}, ${day.date}`}>
              <h3 className="operator-completed-date">{day.date}</h3>
              <ul className="operator-completed-list">
                {day.orders.map((order) => (
                  <li key={order.orderId} className="operator-completed-order">
                    <span className="operator-completed-id">Order {order.orderId}</span>
                    <span className="operator-completed-time">{order.time}</span>
                    <span>{order.customer}</span>
                    <span className="money">{formatMoney(order.totalCents)}</span>
                    <button
                      type="button"
                      className="store-order-button store-order-button-secondary"
                      aria-label={`Refund order ${order.orderId}`}
                    >
                      Refund
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      </section>
    );
  });
}
