package com.littleisles.domain.prc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardPricingStrategyTest {
    private static final PricingConfiguration CONFIGURATION = new PricingConfiguration(
            299, 50, 150, 1_500, 250);
    private final StandardPricingStrategy strategy = new StandardPricingStrategy();

    @Test
    void appliesFixedFeesAndSmallOrderSurchargeAtTheSameLocation() {
        PricingFacts facts = facts(List.of(new PricedOrderLine(500, 2)),
                new GeoPoint(45.5017, -73.5673), new GeoPoint(45.5017, -73.5673));

        Fare fare = strategy.calculate(facts, CONFIGURATION);

        assertEquals(1_000, fare.subtotalCents());
        assertEquals(299, fare.deliveryFeeCents());
        assertEquals(150, fare.serviceFeeCents());
        assertEquals(250, fare.smallOrderSurchargeCents());
        assertEquals(1_699, fare.totalCents());
    }

    @Test
    void doesNotApplySurchargeAtTheExactThreshold() {
        PricingFacts facts = facts(List.of(new PricedOrderLine(750, 2)),
                new GeoPoint(45.5017, -73.5673), new GeoPoint(45.5017, -73.5673));

        Fare fare = strategy.calculate(facts, CONFIGURATION);

        assertEquals(0, fare.smallOrderSurchargeCents());
        assertEquals(1_949, fare.totalCents());
    }

    @Test
    void usesTheFixedDeliveryFeeRegardlessOfDistance() {
        PricingFacts facts = facts(List.of(new PricedOrderLine(1_500, 1)),
                new GeoPoint(0.0, 0.0), new GeoPoint(0.0, 1.0));

        Fare fare = strategy.calculate(facts, CONFIGURATION);

        assertEquals(299, fare.deliveryFeeCents());
        assertEquals(1_949, fare.totalCents());
    }

    @Test
    void producesTheSameFareForTheSameFacts() {
        PricingFacts facts = facts(List.of(
                        new PricedOrderLine(425, 2),
                        new PricedOrderLine(695, 1)),
                new GeoPoint(45.5234, -73.5801),
                new GeoPoint(45.5158, -73.5772));

        assertEquals(strategy.calculate(facts, CONFIGURATION),
                strategy.calculate(facts, CONFIGURATION));
    }

    private static PricingFacts facts(List<PricedOrderLine> lines,
                                      GeoPoint store, GeoPoint delivery) {
        return new PricingFacts(42, lines, store, delivery);
    }
}
