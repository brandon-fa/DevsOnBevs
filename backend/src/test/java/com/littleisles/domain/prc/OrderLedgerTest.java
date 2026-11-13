package com.littleisles.domain.prc;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderLedgerTest {
    @Test
    void recordsFinalStateTimestampsAndTotalCharge() {
        OrderLedger ledger = new OrderLedger();
        LedgerEntry entry = LedgerEntry.charge(
                42,
                "delivered",
                List.of(
                        new OrderTimestamp("placed", Instant.parse("2026-11-07T18:00:00Z")),
                        new OrderTimestamp("delivered", Instant.parse("2026-11-07T18:45:00Z"))),
                2_345,
                Instant.parse("2026-11-07T18:45:01Z"));

        ledger.append(entry);

        assertEquals(List.of(entry), ledger.entries());
        assertEquals(List.of(entry), ledger.entriesForOrder(42));
    }

    @Test
    void preservesTheSingleOriginalChargeForAnOrder() {
        OrderLedger ledger = new OrderLedger();
        ledger.append(charge(42));

        assertThrows(IllegalStateException.class, () -> ledger.append(charge(42)));
    }

    private static LedgerEntry charge(long orderId) {
        return LedgerEntry.charge(orderId, "delivered", List.of(), 1_000,
                Instant.parse("2026-11-07T18:00:00Z"));
    }
}
