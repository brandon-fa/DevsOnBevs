package com.littleisles.domain.prc;

public record Fare(long subtotalCents,
                   long deliveryFeeCents,
                   long serviceFeeCents,
                   long smallOrderSurchargeCents,
                   long totalCents) {
    public Fare {
        if (subtotalCents < 0 || deliveryFeeCents < 0 || serviceFeeCents < 0
                || smallOrderSurchargeCents < 0 || totalCents < 0) {
            throw new IllegalArgumentException("Fare amounts cannot be negative.");
        }
        long expectedTotal = Math.addExact(
                Math.addExact(subtotalCents, deliveryFeeCents),
                Math.addExact(serviceFeeCents, smallOrderSurchargeCents));
        if (totalCents != expectedTotal) {
            throw new IllegalArgumentException("The fare total must equal all fare components.");
        }
    }
}
