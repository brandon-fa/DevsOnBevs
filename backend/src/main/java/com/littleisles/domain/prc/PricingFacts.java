package com.littleisles.domain.prc;

import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.OrderLine;

import java.util.List;
import java.util.Objects;

public record PricingFacts(long orderId, List<PricedOrderLine> orderLines,
                           GeoPoint storeLocation, GeoPoint deliveryLocation) {
    public PricingFacts {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Pricing facts need a positive order id.");
        }
        if (orderLines == null || orderLines.isEmpty()) {
            throw new IllegalArgumentException("Pricing facts need at least one order line.");
        }
        orderLines = List.copyOf(orderLines);
        if (orderLines.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Pricing facts cannot contain a null order line.");
        }
        Objects.requireNonNull(storeLocation, "Pricing facts need the grocery-store location.");
        Objects.requireNonNull(deliveryLocation, "Pricing facts need the delivery location.");
    }

    public static PricingFacts from(Order order, GeoPoint deliveryLocation) {
        Objects.requireNonNull(order, "An order is required for pricing.");
        List<PricedOrderLine> lines = order.getOrderLines().stream()
                .map(PricingFacts::pricedLine)
                .toList();
        return new PricingFacts(
                order.getOrderId(),
                lines,
                new GeoPoint(order.getStore().getLat(), order.getStore().getLon()),
                deliveryLocation);
    }

    private static PricedOrderLine pricedLine(OrderLine line) {
        return new PricedOrderLine(line.getUnitPriceCents(), line.getQuantity());
    }
}
