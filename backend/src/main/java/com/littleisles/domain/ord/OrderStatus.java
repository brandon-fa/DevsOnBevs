package com.littleisles.domain.ord;

/**
 * Order states represented by the Phase II ORD model.
 *
 * <p>The transitions between these states belong to the order lifecycle
 * behavior (UC-ORD-04); this enum only names the states.</p>
 */
public enum OrderStatus {
    PLACED("placed"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    CANCELLED("cancelled"),
    PREPARING("preparing"),
    READY("ready"),
    PICKED_UP("picked_up"),
    // Present in the existing database seed/schema; later delivery behavior is out of scope here.
    ON_THE_WAY("on_the_way"),
    DELIVERED("delivered");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static OrderStatus fromLabel(String label) {
        if (label != null) {
            for (OrderStatus status : values()) {
                if (status.label.equals(label.trim())) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("Unknown order status: " + label);
    }
}
