package com.littleisles.domain.ord;

public record CustomerPin(String id, String name, double lat, double lon) {
    public CustomerPin {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A customer needs an id.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A customer needs a name.");
        }
    }
}
