package com.littleisles.domain.prc;

public interface PricingStrategy {
    Fare calculate(PricingFacts facts, PricingConfiguration configuration);
}
