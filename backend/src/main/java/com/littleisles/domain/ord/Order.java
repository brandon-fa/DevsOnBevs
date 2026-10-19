package com.littleisles.domain.ord;

import java.time.Instant;
import java.util.List;

/**
 * An order shared by the order placement and preparation use cases.
 * A null id represents a new order that has not yet been persisted.
 */
public record Order(
        Long id,
        long customerId,
        long restaurantId,
        List<OrderLine> lines,
        OrderStatus status,
        Instant createdAt,
        Instant acceptedAt
) {
    public Order {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("An order id must be positive when assigned.");
        }
        if (customerId <= 0) {
            throw new IllegalArgumentException("An order needs a valid customer id.");
        }
        if (restaurantId <= 0) {
            throw new IllegalArgumentException("An order needs a valid restaurant id.");
        }
        lines = List.copyOf(lines == null ? List.of() : lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one line.");
        }
        if (status == null) {
            throw new IllegalArgumentException("An order needs a status.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("An order needs a creation time.");
        }
        if (requiresAcceptanceTime(status) && acceptedAt == null) {
            throw new IllegalArgumentException("An accepted order needs its acceptance time.");
        }
        if (!requiresAcceptanceTime(status) && acceptedAt != null) {
            throw new IllegalArgumentException("An order cannot have an acceptance time before acceptance.");
        }
    }

    /** Creates the initial, unsaved state required by UC-ORD-02. */
    public static Order placed(long customerId, long restaurantId, List<OrderLine> lines, Instant createdAt) {
        return new Order(null, customerId, restaurantId, lines, OrderStatus.PLACED, createdAt, null);
    }

    /** Returns this new order with the id assigned by persistence. */
    public Order withAssignedId(long assignedId) {
        if (id != null) {
            throw new IllegalStateException("This order already has a persistent id.");
        }
        return new Order(assignedId, customerId, restaurantId, lines, status, createdAt, acceptedAt);
    }

    private static boolean requiresAcceptanceTime(OrderStatus status) {
        return switch (status) {
            case ACCEPTED, PREPARING, READY, PICKED_UP, ON_THE_WAY, DELIVERED -> true;
            case PLACED, REJECTED, CANCELLED -> false;
        };
    }
}
