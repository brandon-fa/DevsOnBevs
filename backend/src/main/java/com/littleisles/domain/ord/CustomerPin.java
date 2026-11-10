package com.littleisles.domain.ord;

import java.util.List;

public record CustomerPin(
        String id,
        String name,
        double lat,
        double lon,
        String address,
        List<CustomerOrderRef> orders
) {
    public CustomerPin {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A customer needs an id.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A customer needs a name.");
        }
        if (address == null) address = "";
        if (orders == null) orders = List.of();
        else orders = List.copyOf(orders);
    }

    public CustomerPin(String id, String name, double lat, double lon) {
        this(id, name, lat, lon, "", List.of());
    }

    public record CustomerOrderRef(int orderId, String status, String storeName) {
        public CustomerOrderRef {
            if (storeName == null) storeName = "";
            if (status == null) status = "";
        }
    }
}
