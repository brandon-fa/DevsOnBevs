package com.littleisles.domain.prc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class OrderLedger {
    private final List<LedgerEntry> entries = new ArrayList<>();

    public OrderLedger() {
    }

    public OrderLedger(Collection<LedgerEntry> existingEntries) {
        if (existingEntries != null) {
            for (LedgerEntry entry : existingEntries) {
                append(entry);
            }
        }
    }

    public synchronized void append(LedgerEntry entry) {
        Objects.requireNonNull(entry, "A ledger entry cannot be null.");
        if (entries.stream().anyMatch(existing -> existing.entryId().equals(entry.entryId()))) {
            throw new IllegalArgumentException("The ledger already contains this entry.");
        }
        if (entry.entryType() == LedgerEntryType.CHARGE
                && entries.stream().anyMatch(existing -> existing.orderId() == entry.orderId()
                && existing.entryType() == LedgerEntryType.CHARGE)) {
            throw new IllegalStateException("An order can have only one original charge entry.");
        }
        entries.add(entry);
    }

    public synchronized List<LedgerEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized List<LedgerEntry> entriesForOrder(long orderId) {
        return entries.stream().filter(entry -> entry.orderId() == orderId).toList();
    }
}
