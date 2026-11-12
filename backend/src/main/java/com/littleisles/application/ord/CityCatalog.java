package com.littleisles.application.ord;

import com.littleisles.domain.ord.CitySnapshot;
import com.littleisles.domain.ord.CourierPin;
import com.littleisles.domain.ord.CourierStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CityCatalog {
    private CitySnapshot snapshot;
    private final Set<String> openOffers = new HashSet<>();

    public CityCatalog(CitySnapshot snapshot) {
        replace(snapshot);
    }

    public synchronized CitySnapshot snapshot() {
        return snapshot;
    }

    /** Reset: swap in a freshly loaded city from the configuration file. */
    public synchronized void replace(CitySnapshot freshSnapshot) {
        if (freshSnapshot == null) {
            throw new IllegalArgumentException("The city catalog needs a loaded snapshot.");
        }
        this.snapshot = freshSnapshot;
        openOffers.clear(); // a reset also forgets pending courier offers
    }

    public synchronized boolean hasOpenOffer(String courierPinId) {
        return openOffers.contains(courierPinId);
    }

    public synchronized void markOpenOffer(String courierPinId) {
        openOffers.add(courierPinId);
    }

    public synchronized void clearOpenOffer(String courierPinId) {
        openOffers.remove(courierPinId);
    }

    public synchronized void overlayStoredCouriers(List<CourierPin> stored) {
        if (stored == null || stored.isEmpty()) {
            return;
        }
        List<CourierPin> updated = new ArrayList<>(snapshot.couriers());
        for (CourierPin pin : stored) {
            boolean replaced = false;
            for (int index = 0; index < updated.size(); index++) {
                if (updated.get(index).id().equals(pin.id())) {
                    updated.set(index, pin);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                updated.add(pin);
            }
        }
        snapshot = new CitySnapshot(snapshot.bounds(), snapshot.groceryStores(), updated, snapshot.customers());
    }

    public synchronized void setCourierStatus(String courierPinId, CourierStatus status) {
        replaceCourier(courierPinId, null, null, status);
    }

    public synchronized void setCourierLocation(String courierPinId, double lat, double lon) {
        replaceCourier(courierPinId, lat, lon, null);
    }

    private void replaceCourier(String courierPinId, Double lat, Double lon, CourierStatus status) {
        if (courierPinId == null || courierPinId.isBlank()) {
            throw new IllegalArgumentException("A courier update needs a pin id.");
        }
        List<CourierPin> updated = new ArrayList<>();
        boolean found = false;
        for (CourierPin pin : snapshot.couriers()) {
            if (pin.id().equals(courierPinId)) {
                updated.add(new CourierPin(
                        pin.id(),
                        pin.name(),
                        lat == null ? pin.lat() : lat,
                        lon == null ? pin.lon() : lon,
                        status == null ? pin.status() : status));
                found = true;
            } else {
                updated.add(pin);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("The courier is not on the city map.");
        }
        snapshot = new CitySnapshot(snapshot.bounds(), snapshot.groceryStores(), updated, snapshot.customers());
    }
}