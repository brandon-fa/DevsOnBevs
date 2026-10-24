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
    description: "Browse markets and your delivery address. Your courier appears after pickup.",
  },
  groceries: {
    title: "Market operations map",
    description: "Review market locations, courier activity, and customer delivery pins.",
  },
  courier: {
    title: "Courier stops map",
    description: "Review market pickup locations and customer delivery stops available in the city.",
  },
  operator: {
    title: "City operations map",
    description: "Monitor all market, courier, and customer locations across the seeded city.",
  },
};

function markerIcon(kind, status) {
  return L.divIcon({
    className: "city-marker",
    html: `<span class="city-marker-dot is-${kind} is-${status || "default"}"></span>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
    popupAnchor: [0, -12],
  });
}

function normalizedName(value) {
  return value?.trim().toLocaleLowerCase() ?? "";
}

export default function CityMap({ role, account, order, onFeedback }) {
  const mapNode = useRef(null);
  const mapRef = useRef(null);
  const [city, setCity] = useState(null);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);
  const mapCopy = MAP_COPY[role] ?? MAP_COPY.operator;

  useEffect(() => {
    let cancelled = false;
    fetch("/api/city", { credentials: "include" })
      .then(async (response) => {
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
          throw new Error(payload.error || "The city could not be loaded.");
        }
        return payload;
      })
      .then((payload) => {
        if (!cancelled) setCity(payload);
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(loadError.message || "The city could not be loaded.");
          onFeedback(loadError.message || "The city could not be loaded.", "error");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [onFeedback]);

  const visibleCity = useMemo(() => {
    if (!city) return null;
    if (role !== "customer") return city;

    const customer = city.customers.find(
      (pin) => normalizedName(pin.name) === normalizedName(account?.fullName),
    );
    const isPickedUp = ["transit", "picked_up", "on_the_way"].includes(order?.status);
    const courier = order?.courierId && isPickedUp
      ? city.couriers.find((pin) => pin.id === order?.courierId)
      : null;

    return {
      ...city,
      couriers: courier ? [courier] : [],
      customers: customer ? [customer] : [],
    };
  }, [account?.fullName, city, order?.courierId, order?.status, role]);

  const isEmpty = useMemo(() => {
    if (!visibleCity) return false;
    return visibleCity.groceryStores.length + visibleCity.couriers.length + visibleCity.customers.length === 0;
  }, [visibleCity]);

  useEffect(() => {
    if (!visibleCity || isEmpty || !mapNode.current) return undefined;

    const map = L.map(mapNode.current, { scrollWheelZoom: false, attributionControl: true });
    mapRef.current = map;
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "&copy; OpenStreetMap",
    }).addTo(map);

    const bounds = L.latLngBounds(
      [visibleCity.bounds.minLat, visibleCity.bounds.minLon],
      [visibleCity.bounds.maxLat, visibleCity.bounds.maxLon],
    );
    map.fitBounds(bounds, { padding: [24, 24] });

    visibleCity.groceryStores.forEach((groceryStore) => {
      const marker = L.marker([groceryStore.lat, groceryStore.lon], {
        icon: markerIcon("groceryStore", groceryStore.open ? "open" : "closed"),
        title: groceryStore.name,
      }).addTo(map);
      marker.bindPopup(`${groceryStore.name} · ${groceryStore.open ? "open" : "closed"}`);
      marker.on("click", () => setSelected({ kind: "groceryStore", ...groceryStore }));
    });

    visibleCity.couriers.forEach((courier) => {
      const marker = L.marker([courier.lat, courier.lon], {
        icon: markerIcon("courier", courier.status),
        title: `Courier ${courier.id}`,
      }).addTo(map);
      marker.bindPopup(`Courier ${courier.id} · ${courier.status}`);
      marker.on("click", () => setSelected({ kind: "courier", ...courier }));
    });

    visibleCity.customers.forEach((customer) => {
      const marker = L.marker([customer.lat, customer.lon], {
        icon: markerIcon("customer"),
        title: customer.name,
      }).addTo(map);
      marker.bindPopup(`${customer.name}`);
      marker.on("click", () => setSelected({ kind: "customer", ...customer }));
    });

    const trackedCourier = visibleCity.couriers.find((courier) => courier.id === order?.courierId);
    const deliveryPin = visibleCity.customers[0];
    if (role === "customer" && trackedCourier && deliveryPin) {
      L.polyline(
        [[trackedCourier.lat, trackedCourier.lon], [deliveryPin.lat, deliveryPin.lon]],
        { color: "#87543c", weight: 3, dashArray: "7 8" },
      ).addTo(map);
    }

    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, [isEmpty, order?.courierId, role, visibleCity]);

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
            {selected ? <SelectedDetails selected={selected} /> : <p>Select a marker to read its seeded details.</p>}
            {role === "customer" && !visibleCity.customers.length && (
              <p className="city-map-note">No seeded delivery pin matches this customer account.</p>
            )}
            {role === "customer" && visibleCity.couriers.length > 0 && (
              <p className="city-map-note">Showing the seeded courier location; this demo does not update it live.</p>
            )}
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
        <p className="city-map-status">Status: {selected.open ? "open" : "closed"}</p>
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
        <h3>{selected.id}</h3>
        <p className="city-map-status">Status: {selected.status}</p>
      </>
    );
  }
  return (
    <>
      <p className="section-kicker">{KIND_LABEL.customer}</p>
      <h3>{selected.name}</h3>
      <p className="city-map-status">
        Delivery pin: {selected.lat.toFixed(4)}, {selected.lon.toFixed(4)}
      </p>
    </>
  );
}
