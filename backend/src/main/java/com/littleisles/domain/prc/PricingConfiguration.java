package com.littleisles.domain.prc;

public record PricingConfiguration(long baseDeliveryFeeCents,
                                   long deliveryRateCentsPerKilometre,
                                   long serviceFeeCents,
                                   long smallOrderThresholdCents,
                                   long smallOrderSurchargeCents) {
    public PricingConfiguration {
        if (baseDeliveryFeeCents < 0
                || deliveryRateCentsPerKilometre < 0
                || serviceFeeCents < 0
                || smallOrderThresholdCents < 0
                || smallOrderSurchargeCents < 0) {
            throw new IllegalArgumentException("Pricing configuration values cannot be negative.");
        }
    }
}
