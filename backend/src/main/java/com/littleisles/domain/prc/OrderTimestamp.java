package com.littleisles.domain.prc;

import java.time.Instant;

public record OrderTimestamp(String state, Instant occurredAt) {
    public OrderTimestamp {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("An order timestamp needs a state.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("An order timestamp needs a time.");
        }
        state = state.trim();
    }
}
