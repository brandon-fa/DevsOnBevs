package com.littleisles.application.prc;

import com.littleisles.domain.prc.Fare;
import com.littleisles.domain.prc.PricingConfiguration;
import com.littleisles.domain.prc.PricingFacts;
import com.littleisles.domain.prc.PricingStrategy;

import java.util.Objects;

public final class PricingService {
    private final PricingStrategy strategy;
    private final PricingConfiguration configuration;

    public PricingService(PricingStrategy strategy, PricingConfiguration configuration) {
        this.strategy = Objects.requireNonNull(strategy, "A pricing strategy is required.");
        this.configuration = Objects.requireNonNull(configuration, "Pricing configuration is required.");
    }

    public Fare calculate(PricingFacts facts) {
        return strategy.calculate(facts, configuration);
    }
}
