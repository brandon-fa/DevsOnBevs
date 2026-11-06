import { useEffect, useMemo, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { formatMoney } from "../../data/mockData.js";
import "./city-map.css";

const KIND_LABEL = {
  groceryStore: "Market",
  courier: "Courier",
  customer: "Customer",
};

const MAP_COPY = {
  customer: {
    title: "Markets and your delivery",
    description: "Browse markets and your delivery addresses. Your courier appears after pickup.",
  },
  groceries: {
    title: "Market operations map",
    description: "Review market locations, courier activity, and customers ordering from your store.",
  },
  courier: {
    title: "Courier stops map",
    description: "See pickup markets and the customer stop for the delivery you accepted.",
  },
  operator: {
    title: "City operations map",
    description: "Monitor all market, courier, and customer locations across the city.",
  },
};

const MARKER_GLYPH = {
  groceryStore: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3.5 10.5 5.2 6h13.6l1.7 4.5"/><path d="M4 10.5h16V19a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z"/><path d="M10 20v-5h4v5"/></svg>`,
  customer: `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.2"/><path d="M6.2 19.2c.8-3 2.9-4.5 5.8-4.5s5 1.5 5.8 4.5"/></svg>`,
  courier: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4.5 8.2 12 4.4l7.5 3.8v8.4L12 20.4l-7.5-3.8z"/><path d="M4.5 8.2 12 12l7.5-3.8"/><path d="M12 12v8.4"/><path d="M12 12 8.2 10.1"/></svg>`,
};

/** Small NE nudge so a courier and market sharing a stop stay both visible. */
const COLOCATED_COURIER_OFFSET = 0.00045;

function markerIcon(kind, status) {
  return L.divIcon({
    className: "city-marker",
    html: `<span class="city-marker-dot is-${kind} is-${status || "default"}">${MARKER_GLYPH[kind] || ""}</span>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
    popupAnchor: [0, -14],
  });
}

function positionKey(lat, lon) {
  return `${Number(lat).toFixed(5)},${Number(lon).toFixed(5)}`;
}

function courierDisplayPosition(courier, occupiedKeys) {
  let lat = courier.lat;
  let lon = courier.lon;
  const key = positionKey(lat, lon);
  if (occupiedKeys.has(key)) {
    lat += COLOCATED_COURIER_OFFSET;
    lon += COLOCATED_COURIER_OFFSET;
  }
  occupiedKeys.add(positionKey(lat, lon));
  return [lat, lon];
}

export default function CityMap({ role, account, order, onFeedback }) {
  const mapNode = useRef(null);
  const mapRef = useRef(null);
  const markersLayerRef = useRef(null);
  const hasFittedRef = useRef(false);
  const [city, setCity] = useState(null);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);
  const mapCopy = MAP_COPY[role] ?? MAP_COPY.operator;

  useEffect(() => {
    let cancelled = false;
    let timer;
    const controller = new AbortController();

    async function load() {
      try {
        const response = await fetch("/api/city", { credentials: "include", signal: controller.signal });
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
          throw new Error(payload.error || "The city could not be loaded.");
        }
        if (!cancelled) {
          setCity(payload);
          setError("");
        }
      } catch (loadError) {
        if (!cancelled && loadError.name !== "AbortError") {
          setError(loadError.message || "The city could not be loaded.");
          onFeedback?.(loadError.message || "The city could not be loaded.", "error");
        }
      } finally {
        if (!cancelled) timer = window.setTimeout(load, 5000);
      }
    }

    load();
    return () => {
      cancelled = true;
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [onFeedback]);

  const visibleCity = city;

  const isEmpty = useMemo(() => {
    if (!visibleCity) return false;
    return visibleCity.groceryStores.length + visibleCity.couriers.length + visibleCity.customers.length === 0;
  }, [visibleCity]);

  useEffect(() => {
    return () => {
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
        markersLayerRef.current = null;
        hasFittedRef.current = false;
      }
    };
  }, []);

  useEffect(() => {
    if (!visibleCity || isEmpty || !mapNode.current) return;

    let map = mapRef.current;
    if (!map) {
      map = L.map(mapNode.current, { scrollWheelZoom: false, attributionControl: true });
      mapRef.current = map;
      L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 19,
        attribution: "&copy; OpenStreetMap",
      }).addTo(map);
      markersLayerRef.current = L.layerGroup().addTo(map);
    }

    const layer = markersLayerRef.current;
    layer.clearLayers();

    const occupiedKeys = new Set();

    visibleCity.groceryStores.forEach((groceryStore) => {
      occupiedKeys.add(positionKey(groceryStore.lat, groceryStore.lon));
      const marker = L.marker([groceryStore.lat, groceryStore.lon], {
        icon: markerIcon("groceryStore", groceryStore.open ? "open" : "closed"),
        title: groceryStore.name,
        zIndexOffset: 200,
      });
      marker.bindPopup(`${groceryStore.name} · ${groceryStore.open ? "open" : "closed"}`);
      marker.on("click", () => setSelected({ kind: "groceryStore", ...groceryStore }));
      layer.addLayer(marker);
    });

    visibleCity.customers.forEach((customer) => {
      occupiedKeys.add(positionKey(customer.lat, customer.lon));
      const marker = L.marker([customer.lat, customer.lon], {
        icon: markerIcon("customer"),
        title: customer.name,
        zIndexOffset: 100,
      });
      const orderCount = (customer.orders || []).length;
      marker.bindPopup(
        orderCount > 0
          ? `${customer.name} · ${orderCount} open order${orderCount === 1 ? "" : "s"}`
          : customer.name,
      );
      marker.on("click", () => setSelected({ kind: "customer", ...customer }));
      layer.addLayer(marker);
    });

    visibleCity.couriers.forEach((courier) => {
      const [lat, lon] = courierDisplayPosition(courier, occupiedKeys);
      const marker = L.marker([lat, lon], {
        icon: markerIcon("courier"),
        title: courier.name || `Courier ${courier.id}`,
        zIndexOffset: 300,
      });
      marker.bindPopup(`${courier.name || courier.id} · ${courier.status}`);
      marker.on("click", () => setSelected({ kind: "courier", ...courier }));
      layer.addLayer(marker);
    });

    const isPickedUp = ["transit", "picked_up", "on_the_way"].includes(order?.status);
    const trackedCourier = isPickedUp
      ? visibleCity.couriers.find((courier) => courier.id === order?.courierId)
      : null;
    const deliveryPin = visibleCity.customers[0];
    if (role === "customer" && trackedCourier && deliveryPin) {
      const line = L.polyline(
        [[trackedCourier.lat, trackedCourier.lon], [deliveryPin.lat, deliveryPin.lon]],
        { color: "#87543c", weight: 3, dashArray: "7 8" },
      );
      layer.addLayer(line);
    }

    if (!hasFittedRef.current) {
      const bounds = L.latLngBounds(
        [visibleCity.bounds.minLat, visibleCity.bounds.minLon],
        [visibleCity.bounds.maxLat, visibleCity.bounds.maxLon],
      );
      map.fitBounds(bounds, { padding: [24, 24] });
      hasFittedRef.current = true;
    }
  }, [isEmpty, order?.courierId, order?.status, role, visibleCity]);

  return (
    <section className="dash-section city-map-section" aria-labelledby="city-map-title">
      <div className="section-heading">
        <h2 id="city-map-title">{mapCopy.title}</h2>
        <p>{mapCopy.description}</p>
      </div>
      {error ? null : !city ? (
        <p className="city-map-empty">Loading the city…</p>
      ) : isEmpty ? (
        <p className="city-map-empty">No map locations are available for this account.</p>
      ) : (
        <div className="city-map-layout">
          <div className="city-map-canvas" ref={mapNode} role="application" aria-label="Little Isles city map" />
          <aside className="city-map-details" aria-live="polite">
            {selected ? <SelectedDetails selected={selected} /> : <p>Select a marker to read its details.</p>}
          </aside>
        </div>
      )}
    </section>
  );
}

function SelectedDetails({ selected }) {
  if (selected.kind === "groceryStore") {
    return (
      <>
        <p className="section-kicker">{KIND_LABEL.groceryStore}</p>
        <h3>{selected.name}</h3>
        <p className={`city-status-label ${selected.open ? "is-open" : "is-closed"}`}>
          {selected.open ? "Open" : "Closed"}
        </p>
        <ul className="city-product-list">
          {selected.products.map((product) => (
            <li key={product.id}>
              <span>
                {product.name}
                <span className="city-item-status"> · {product.soldOut ? "sold out" : "available"}</span>
              </span>
              <span className="money">{formatMoney(product.priceCents)}</span>
            </li>
          ))}
        </ul>
      </>
    );
  }
  if (selected.kind === "courier") {
    return (
      <>
        <p className="section-kicker">{KIND_LABEL.courier}</p>
        <h3>{selected.name || selected.id}</h3>
        <p className="city-map-status">Status: {selected.status}</p>
      </>
    );
  }
  const orders = selected.orders || [];
  return (
    <>
      <p className="section-kicker">{KIND_LABEL.customer}</p>
      <h3>{selected.name}</h3>
      {selected.address && <p className="city-map-status">Address: {selected.address}</p>}
      <p className="city-map-status">
        Pin: {selected.lat.toFixed(4)}, {selected.lon.toFixed(4)}
      </p>
      {orders.length === 0 ? (
        <p className="city-map-note">No open orders at this address.</p>
      ) : (
        <ul className="city-product-list">
          {orders.map((order) => (
            <li key={order.orderId}>
              <span>
                Order {order.orderId}
                {order.storeName ? ` · ${order.storeName}` : ""}
              </span>
              <span>{order.status}</span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
