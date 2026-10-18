import { useEffect, useMemo, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { formatMoney } from "../../data/mockData.js";
import "./city-map.css";

const KIND_LABEL = {
  restaurant: "Market",
  courier: "Courier",
  customer: "Customer",
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

export default function CityMap() {
  const mapNode = useRef(null);
  const mapRef = useRef(null);
  const [city, setCity] = useState(null);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);

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
        if (!cancelled) setError(loadError.message || "The city could not be loaded.");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const isEmpty = useMemo(() => {
    if (!city) return false;
    return city.restaurants.length + city.couriers.length + city.customers.length === 0;
  }, [city]);

  useEffect(() => {
    if (!city || isEmpty || !mapNode.current) return undefined;

    const map = L.map(mapNode.current, { scrollWheelZoom: false, attributionControl: true });
    mapRef.current = map;
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "&copy; OpenStreetMap",
    }).addTo(map);

    const bounds = L.latLngBounds(
      [city.bounds.minLat, city.bounds.minLon],
      [city.bounds.maxLat, city.bounds.maxLon],
    );
    map.fitBounds(bounds, { padding: [24, 24] });

    city.restaurants.forEach((restaurant) => {
      const marker = L.marker([restaurant.lat, restaurant.lon], {
        icon: markerIcon("restaurant", restaurant.status),
        title: restaurant.name,
      }).addTo(map);
      marker.bindPopup(`${restaurant.name} · ${restaurant.status}`);
      marker.on("click", () => setSelected({ kind: "restaurant", ...restaurant }));
    });

    city.couriers.forEach((courier) => {
      const marker = L.marker([courier.lat, courier.lon], {
        icon: markerIcon("courier", courier.status),
        title: `Courier ${courier.id}`,
      }).addTo(map);
      marker.bindPopup(`Courier ${courier.id} · ${courier.status}`);
      marker.on("click", () => setSelected({ kind: "courier", ...courier }));
    });

    city.customers.forEach((customer) => {
      const marker = L.marker([customer.lat, customer.lon], {
        icon: markerIcon("customer"),
        title: customer.name,
      }).addTo(map);
      marker.bindPopup(`${customer.name}`);
      marker.on("click", () => setSelected({ kind: "customer", ...customer }));
    });

    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, [city, isEmpty]);

  return (
    <section className="dash-section city-map-section" aria-labelledby="city-map-title">
      <div className="section-heading">
        <h2 id="city-map-title">City map</h2>
        <p>Markets, couriers, and delivery addresses from the seeded city file.</p>
      </div>
      {error ? (
        <p className="city-map-empty" role="alert">
          {error}
        </p>
      ) : !city ? (
        <p className="city-map-empty">Loading the city…</p>
      ) : isEmpty ? (
        <p className="city-map-empty">The city file loaded, and the map is empty.</p>
      ) : (
        <div className="city-map-layout">
          <div className="city-map-canvas" ref={mapNode} role="application" aria-label="Little Isles city map" />
          <aside className="city-map-details" aria-live="polite">
            {selected ? <SelectedDetails selected={selected} /> : <p>Select a marker to read its seeded details.</p>}
          </aside>
        </div>
      )}
    </section>
  );
}

function SelectedDetails({ selected }) {
  if (selected.kind === "restaurant") {
    return (
      <>
        <p className="section-kicker">{KIND_LABEL.restaurant}</p>
        <h3>{selected.name}</h3>
        <p className="city-map-status">Status: {selected.status}</p>
        <ul className="city-menu-list">
          {selected.menu.map((item) => (
            <li key={item.id}>
              <span>
                {item.name}
                <span className="city-item-status"> · {item.status}</span>
              </span>
              <span className="money">{formatMoney(item.priceCents)}</span>
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
