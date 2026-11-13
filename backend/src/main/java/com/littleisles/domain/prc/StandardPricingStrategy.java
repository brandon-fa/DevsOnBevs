package com.littleisles.domain.prc;

import java.util.Objects;

public final class StandardPricingStrategy implements PricingStrategy {
    @Override
    public Fare calculate(PricingFacts facts, PricingConfiguration configuration) {
        Objects.requireNonNull(facts, "Pricing facts are required.");
        Objects.requireNonNull(configuration, "Pricing configuration is required.");

        long subtotalCents = 0;
        for (PricedOrderLine line : facts.orderLines()) {
            subtotalCents = Math.addExact(subtotalCents, line.subtotalCents());
        }

        long deliveryFeeCents = configuration.baseDeliveryFeeCents();
        long surchargeCents = subtotalCents < configuration.smallOrderThresholdCents()
                ? configuration.smallOrderSurchargeCents()
                : 0;
        long totalCents = Math.addExact(
                Math.addExact(subtotalCents, deliveryFeeCents),
                Math.addExact(configuration.serviceFeeCents(), surchargeCents));

        return new Fare(subtotalCents, deliveryFeeCents, configuration.serviceFeeCents(),
                surchargeCents, totalCents);
    }
}
