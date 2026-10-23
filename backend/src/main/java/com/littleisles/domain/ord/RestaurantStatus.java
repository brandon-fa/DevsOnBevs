package com.littleisles.domain.ord;

public enum RestaurantStatus {
    OPEN("open"),
    PAUSED("paused"),
    CLOSED("closed");

    private final String label;

    RestaurantStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static RestaurantStatus fromLabel(String label) {
        if (label != null) {
            for (RestaurantStatus status : values()) {
                if (status.label.equals(label.trim())) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("Restaurant status must be open, paused, or closed.");
    }
}
