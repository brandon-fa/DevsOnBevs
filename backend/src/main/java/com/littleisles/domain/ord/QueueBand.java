package com.littleisles.domain.ord;

/**
 * R-DASH-03: how busy a store's preparation queue is.
 * LONG (red) at six or more queued orders, or when the store is paused;
 * BUILDING (amber) at three to five; SHORT (green) otherwise.
 */
public enum QueueBand {
    SHORT("short"),
    BUILDING("building"),
    LONG("long");

    private static final int BUILDING_FROM = 3;
    private static final int LONG_FROM = 6;

    private final String label;

    QueueBand(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static QueueBand of(int queuedOrders, boolean paused) {
        if (paused || queuedOrders >= LONG_FROM) {
            return LONG;
        }
        if (queuedOrders >= BUILDING_FROM) {
            return BUILDING;
        }
        return SHORT;
    }
}