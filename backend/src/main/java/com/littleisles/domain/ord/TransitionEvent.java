package com.littleisles.domain.ord;

import java.time.Instant;
import java.util.Objects;

public class TransitionEvent {
    private String fromState;
    private String toState;
    private String timestamp;
    private String reason;

    public TransitionEvent(String fromState, String toState) {
        this(fromState, toState, null);
    }

    public TransitionEvent(String fromState, String toState, String reason) {
        this(fromState, toState, Instant.now().toString(), reason);
    }

    private TransitionEvent(String fromState, String toState, String timestamp, String reason) {
        this.fromState = requireState(fromState, "from");
        this.toState = requireState(toState, "to");
        this.timestamp = Objects.requireNonNull(timestamp, "A transition timestamp is required.");
        Instant.parse(timestamp);
        this.reason = reason;
    }

    public static TransitionEvent restore(String fromState, String toState, String timestamp, String reason) {
        return new TransitionEvent(fromState, toState, timestamp, reason);
    }

    private static String requireState(String state, String label) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("A transition needs a " + label + " state.");
        }
        return state.trim();
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getReason() {
        return reason;
    }
}
