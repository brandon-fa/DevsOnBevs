package com.littleisles.domain.ord;

public enum CourierStatus {
    OFFLINE("offline"),
    IDLE("idle"),
    ASSIGNED("assigned"),
    CARRYING("carrying");

    private final String label;

    CourierStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static CourierStatus fromLabel(String label) {
        if (label != null) {
            for (CourierStatus status : values()) {
                if (status.label.equals(label.trim())) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("Courier status must be offline, idle, assigned, or carrying.");
    }

    public boolean onDuty() {
        return this != OFFLINE;
    }
}
