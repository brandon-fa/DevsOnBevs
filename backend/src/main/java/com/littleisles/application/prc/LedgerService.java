package com.littleisles.application.prc;

import com.littleisles.domain.prc.Fare;
import com.littleisles.domain.prc.LedgerEntry;
import com.littleisles.domain.prc.OrderLedger;
import com.littleisles.domain.prc.OrderTimestamp;
import com.littleisles.technicalservices.prc.LedgerRepository;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class LedgerService {
    private final OrderLedger ledger;
    private final LedgerRepository repository;
    private final Clock clock;

    public LedgerService(LedgerRepository repository) throws SQLException {
        this(repository, Clock.systemUTC());
    }

    public LedgerService(LedgerRepository repository, Clock clock) throws SQLException {
        this.repository = Objects.requireNonNull(repository, "A ledger repository is required.");
        this.clock = Objects.requireNonNull(clock, "A clock is required.");
        this.ledger = new OrderLedger(repository.findAll());
    }

    public synchronized LedgerEntry recordCharge(long orderId, String finalState,
                                                  List<OrderTimestamp> timestamps,
                                                  Fare fare) throws SQLException {
        Objects.requireNonNull(fare, "A fare is required to record a charge.");
        Instant recordedAt = clock.instant();
        LedgerEntry entry = LedgerEntry.charge(
                orderId, finalState, timestamps, fare.totalCents(), recordedAt);
        OrderLedger prospectiveLedger = new OrderLedger(ledger.entries());
        prospectiveLedger.append(entry);
        repository.save(entry);
        ledger.append(entry);
        return entry;
    }

    public List<LedgerEntry> entries() {
        return ledger.entries();
    }
}
