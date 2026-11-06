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
        if (snapshot == null) {
            throw new IllegalArgumentException("The city catalog needs a loaded snapshot.");
        }
        this.snapshot = snapshot;
    }

    public CitySnapshot snapshot() {
        return snapshot;
    }

    public boolean hasOpenOffer(String courierPinId) {
        return openOffers.contains(courierPinId);
    }

    public void markOpenOffer(String courierPinId) {
        openOffers.add(courierPinId);
    }

    public void clearOpenOffer(String courierPinId) {
        openOffers.remove(courierPinId);
    }

    public void overlayStoredCouriers(List<CourierPin> stored) {
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

    public void setCourierStatus(String courierPinId, CourierStatus status) {
        if (courierPinId == null || courierPinId.isBlank() || status == null) {
            throw new IllegalArgumentException("A courier status update needs a pin and a status.");
        }
        List<CourierPin> updated = new ArrayList<>();
        boolean found = false;
        for (CourierPin pin : snapshot.couriers()) {
            if (pin.id().equals(courierPinId)) {
                updated.add(new CourierPin(pin.id(), pin.name(), pin.lat(), pin.lon(), status));
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
