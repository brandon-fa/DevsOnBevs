package com.littleisles.infrastructure.db;

import com.littleisles.domain.prc.LedgerEntry;
import com.littleisles.domain.prc.OrderTimestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgerStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void persistsAndReloadsLedgerEntriesWithOrderTimestamps() throws Exception {
        Path database = tempDirectory.resolve("ledger.sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY)");
            statement.execute("INSERT INTO orders (id) VALUES (42)");
        }
        LedgerStore store = new LedgerStore(database);
        LedgerEntry entry = LedgerEntry.charge(
                42,
                "delivered",
                List.of(
                        new OrderTimestamp("placed", Instant.parse("2026-11-07T18:00:00Z")),
                        new OrderTimestamp("delivered", Instant.parse("2026-11-07T18:45:00Z"))),
                2_345,
                Instant.parse("2026-11-07T18:45:01Z"));

        store.save(entry);

        assertEquals(List.of(entry), store.findAll());
    }
}
