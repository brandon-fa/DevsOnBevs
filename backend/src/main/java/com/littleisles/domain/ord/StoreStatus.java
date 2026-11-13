package com.littleisles.domain.ord;

/**
 * R-ORD-01: a grocery store is open, paused or closed.
 * Only an open store accepts new orders; a paused store still finishes the orders it already has.
 */
public enum StoreStatus {
    OPEN("open"),
    PAUSED("paused"),
    CLOSED("closed");

    private final String label;

    StoreStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static StoreStatus fromLabel(String label) {
        for (StoreStatus status : values()) {
            if (status.label.equals(label)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Store status must be open, paused or closed: " + label);
    }
}