package com.littleisles.domain.prc;

public record PricedOrderLine(long unitPriceCents, int quantity) {
    public PricedOrderLine {
        if (unitPriceCents < 0) {
            throw new IllegalArgumentException("A unit price cannot be negative.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("An order-line quantity must be positive.");
        }
    }

    public long subtotalCents() {
        return Math.multiplyExact(unitPriceCents, quantity);
    }
}
