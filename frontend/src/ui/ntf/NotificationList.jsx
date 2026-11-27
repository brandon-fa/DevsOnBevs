import { useEffect, useState } from "react";
import "./notifications.css";

// NTF: inline list of the signed-in user's latest notifications, refreshed every 5 seconds.
export default function NotificationList() {
  const [items, setItems] = useState([]);

  useEffect(() => {
    let cancelled = false;
    let timer;
    async function load() {
      try {
        const response = await fetch("/api/notifications", { credentials: "include", cache: "no-store" });
        const body = await response.json().catch(() => ({}));
        if (response.ok && !cancelled) setItems(body.notifications || []);
      } catch {
        // Keep the last list; the next poll retries.
      } finally {
        if (!cancelled) timer = setTimeout(load, 5000);
      }
    }
    load();
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, []);

  return (
    <section className="dash-section notification-panel" aria-label="Notifications">
      <p className="section-kicker">Notifications</p>
      {items.length === 0 && <p className="notification-empty">No notifications yet.</p>}
      <ul className="notification-list" aria-live="polite">
        {items.map((item) => (
          <li key={item.id}>
            <span>{item.message}</span>
            <time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleTimeString()}</time>
          </li>
        ))}
      </ul>
    </section>
  );
}
