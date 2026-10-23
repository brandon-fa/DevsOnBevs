package com.littleisles.domain.ord;

public enum MenuItemStatus {
    AVAILABLE("available"),
    SOLD_OUT("sold_out");

    private final String label;

    MenuItemStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static MenuItemStatus fromLabel(String label) {
        if (label != null) {
            for (MenuItemStatus status : values()) {
                if (status.label.equals(label.trim())) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("Menu item status must be available or sold_out.");
    }
}
