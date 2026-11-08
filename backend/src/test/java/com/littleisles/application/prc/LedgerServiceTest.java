package com.littleisles.application.prc;

import com.littleisles.domain.prc.Fare;
import com.littleisles.domain.prc.LedgerEntry;
import com.littleisles.domain.prc.OrderTimestamp;
import com.littleisles.technicalservices.prc.LedgerRepository;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgerServiceTest {
    @Test
    void recordsTheCalculatedFareAsTheOrdersOriginalCharge() throws Exception {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-11-07T19:00:00Z"), ZoneOffset.UTC);
        LedgerService service = new LedgerService(repository, clock);
        Fare fare = new Fare(1_500, 500, 150, 0, 2_150);

        LedgerEntry entry = service.recordCharge(
                42,
                "delivered",
                List.of(new OrderTimestamp("delivered", Instant.parse("2026-11-07T18:59:00Z"))),
                fare);

        assertEquals(2_150, entry.amountCents());
        assertEquals("delivered", entry.finalState());
        assertEquals(List.of(entry), repository.findAll());
        assertEquals(List.of(entry), service.entries());
    }

    private static final class InMemoryLedgerRepository implements LedgerRepository {
        private final List<LedgerEntry> entries = new ArrayList<>();

        @Override
        public void save(LedgerEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<LedgerEntry> findAll() throws SQLException {
            return List.copyOf(entries);
        }
    }
}
