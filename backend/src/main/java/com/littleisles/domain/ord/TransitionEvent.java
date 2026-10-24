package com.littleisles.domain.ord;

public class TransitionEvent {
    private String fromState;
    private String toState;
    private String timestamp;
    private String reason;

    public TransitionEvent(String fromState, String toState) {
        this(fromState, toState, null);
    }

    public TransitionEvent(String fromState, String toState, String reason) {
        this.fromState = fromState;
        this.toState = toState;
        this.timestamp = java.time.Instant.now().toString();
        this.reason = reason;
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
