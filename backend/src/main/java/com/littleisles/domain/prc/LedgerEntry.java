package com.littleisles.domain.prc;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(UUID entryId,
                          long orderId,
                          LedgerEntryType entryType,
                          String finalState,
                          List<OrderTimestamp> timestamps,
                          long amountCents,
                          Instant recordedAt) {
    public LedgerEntry {
        Objects.requireNonNull(entryId, "A ledger entry needs an id.");
        if (orderId <= 0) {
            throw new IllegalArgumentException("A ledger entry needs a positive order id.");
        }
        Objects.requireNonNull(entryType, "A ledger entry needs a type.");
        if (finalState == null || finalState.isBlank()) {
            throw new IllegalArgumentException("A ledger entry needs the order's final state.");
        }
        finalState = finalState.trim();
        timestamps = timestamps == null ? List.of() : List.copyOf(timestamps);
        if (timestamps.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Ledger timestamps cannot contain null.");
        }
        Objects.requireNonNull(recordedAt, "A ledger entry needs its recording time.");
        if (entryType == LedgerEntryType.CHARGE && amountCents < 0) {
            throw new IllegalArgumentException("A charge amount cannot be negative.");
        }
        if (entryType == LedgerEntryType.REFUND && amountCents > 0) {
            throw new IllegalArgumentException("A refund amount cannot be positive.");
        }
    }

    public static LedgerEntry charge(long orderId, String finalState,
                                     List<OrderTimestamp> timestamps,
                                     long totalChargeCents, Instant recordedAt) {
        return new LedgerEntry(UUID.randomUUID(), orderId, LedgerEntryType.CHARGE,
                finalState, timestamps, totalChargeCents, recordedAt);
    }
}
