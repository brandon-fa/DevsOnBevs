package com.littleisles.domain.ord;

public record CourierPin(String id, String name, double lat, double lon, CourierStatus status) {
    public CourierPin {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A courier needs an id.");
        }
        if (name == null || name.isBlank()) {
            name = id;
        }
        if (status == null) {
            throw new IllegalArgumentException("A courier needs a status.");
        }
    }
}
