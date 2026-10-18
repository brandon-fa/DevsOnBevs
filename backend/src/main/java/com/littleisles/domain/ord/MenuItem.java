package com.littleisles.domain.ord;

public record MenuItem(String id, String name, int priceCents, MenuItemStatus status) {
    public MenuItem {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A menu item needs an id.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A menu item needs a name.");
        }
        if (priceCents < 0) {
            throw new IllegalArgumentException("A menu item price cannot be negative.");
        }
        if (status == null) {
            throw new IllegalArgumentException("A menu item needs a status.");
        }
    }
}
