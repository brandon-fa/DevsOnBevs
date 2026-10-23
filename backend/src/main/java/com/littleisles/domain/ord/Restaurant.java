package com.littleisles.domain.ord;

import java.util.List;

public record Restaurant(String id, String name, RestaurantStatus status, double lat, double lon, List<MenuItem> menu) {
    public Restaurant {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A restaurant needs an id.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A restaurant needs a name.");
        }
        if (status == null) {
            throw new IllegalArgumentException("A restaurant needs a status.");
        }
        menu = List.copyOf(menu == null ? List.of() : menu);
    }
}
