package com.littleisles.application.prc;

import com.littleisles.domain.prc.LedgerEntryType;
import java.util.List;
import java.util.UUID;

public record OrderHistoryRecord(UUID entryId, long orderId, String storeId, String storeName,
                                 LedgerEntryType entryType, String finalState,
                                 List<HistoryTimestamp> timestamps, long amountCents, String recordedAt) {
    public OrderHistoryRecord {
        timestamps = List.copyOf(timestamps);
    }

    public record HistoryTimestamp(String state, String occurredAt) {}
}
