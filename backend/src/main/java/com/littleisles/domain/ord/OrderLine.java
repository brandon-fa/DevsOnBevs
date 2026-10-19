package com.littleisles.domain.ord;

/**
 * One product and quantity requested in an order.
 * The unit price is captured with the line so later catalog changes do not
 * change the price represented by an already placed order.
 */
public record OrderLine(long productId, int quantity, int unitPriceCents) {
    public OrderLine {
        if (productId <= 0) {
            throw new IllegalArgumentException("An order line needs a valid product id.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("An order line quantity must be positive.");
        }
        if (unitPriceCents < 0) {
            throw new IllegalArgumentException("An order line price cannot be negative.");
        }
    }
}
