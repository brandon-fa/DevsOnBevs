package com.littleisles.infrastructure.db;

import com.littleisles.domain.prc.LedgerEntry;
import com.littleisles.domain.prc.LedgerEntryType;
import com.littleisles.domain.prc.OrderTimestamp;
import com.littleisles.technicalservices.prc.LedgerRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LedgerStore implements LedgerRepository {
    private final String jdbcUrl;

    public LedgerStore() {
        this(resolveDatabase());
    }

    public LedgerStore(Path databaseFile) {
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        try {
            Class.forName("org.sqlite.JDBC");
            ensureSchema();
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("SQLite JDBC driver is not on the classpath.", exception);
        } catch (SQLException exception) {
            throw new IllegalStateException("The pricing ledger schema could not be prepared.", exception);
        }
    }

    @Override
    public void save(LedgerEntry entry) throws SQLException {
        String entrySql = """
                INSERT INTO ledger_entries (
                    id, order_id, entry_type, final_state, amount_cents, recorded_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        String timestampSql = """
                INSERT INTO ledger_entry_timestamps (
                    ledger_entry_id, sequence_number, order_state, occurred_at
                ) VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement entryStatement = connection.prepareStatement(entrySql);
                 PreparedStatement timestampStatement = connection.prepareStatement(timestampSql)) {
                entryStatement.setString(1, entry.entryId().toString());
                entryStatement.setLong(2, entry.orderId());
                entryStatement.setString(3, entry.entryType().name());
                entryStatement.setString(4, entry.finalState());
                entryStatement.setLong(5, entry.amountCents());
                entryStatement.setString(6, entry.recordedAt().toString());
                entryStatement.executeUpdate();

                int sequence = 0;
                for (OrderTimestamp timestamp : entry.timestamps()) {
                    timestampStatement.setString(1, entry.entryId().toString());
                    timestampStatement.setInt(2, sequence++);
                    timestampStatement.setString(3, timestamp.state());
                    timestampStatement.setString(4, timestamp.occurredAt().toString());
                    timestampStatement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Override
    public List<LedgerEntry> findAll() throws SQLException {
        String entrySql = """
                SELECT id, order_id, entry_type, final_state, amount_cents, recorded_at
                FROM ledger_entries
                ORDER BY recorded_at, id
                """;
        String timestampSql = """
                SELECT ledger_entry_id, order_state, occurred_at
                FROM ledger_entry_timestamps
                ORDER BY ledger_entry_id, sequence_number
                """;
        try (Connection connection = openConnection();
             Statement entryStatement = connection.createStatement();
             ResultSet entries = entryStatement.executeQuery(entrySql)) {
            Map<UUID, LedgerData> byId = new LinkedHashMap<>();
            while (entries.next()) {
                UUID id = UUID.fromString(entries.getString("id"));
                byId.put(id, new LedgerData(
                        id,
                        entries.getLong("order_id"),
                        LedgerEntryType.valueOf(entries.getString("entry_type")),
                        entries.getString("final_state"),
                        entries.getLong("amount_cents"),
                        Instant.parse(entries.getString("recorded_at")),
                        new ArrayList<>()));
            }
            try (Statement timestampStatement = connection.createStatement();
                 ResultSet timestamps = timestampStatement.executeQuery(timestampSql)) {
                while (timestamps.next()) {
                    LedgerData data = byId.get(UUID.fromString(timestamps.getString("ledger_entry_id")));
                    if (data != null) {
                        data.timestamps().add(new OrderTimestamp(
                                timestamps.getString("order_state"),
                                Instant.parse(timestamps.getString("occurred_at"))));
                    }
                }
            }
            return byId.values().stream().map(LedgerData::toEntry).toList();
        }
    }

    private void ensureSchema() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ledger_entries (
                        id TEXT PRIMARY KEY,
                        order_id INTEGER NOT NULL REFERENCES orders (id),
                        entry_type TEXT NOT NULL CHECK (entry_type IN ('CHARGE', 'REFUND')),
                        final_state TEXT NOT NULL,
                        amount_cents INTEGER NOT NULL,
                        recorded_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ledger_entry_timestamps (
                        ledger_entry_id TEXT NOT NULL REFERENCES ledger_entries (id) ON DELETE CASCADE,
                        sequence_number INTEGER NOT NULL,
                        order_state TEXT NOT NULL,
                        occurred_at TEXT NOT NULL,
                        PRIMARY KEY (ledger_entry_id, sequence_number)
                    )
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS one_charge_per_order
                    ON ledger_entries (order_id)
                    WHERE entry_type = 'CHARGE'
                    """);
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private static Path resolveDatabase() {
        Path[] candidates = {
                Path.of("database", "littleisle.sqlite"),
                Path.of("..", "database", "littleisle.sqlite")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("Could not find database/littleisle.sqlite.");
    }

    private record LedgerData(UUID id, long orderId, LedgerEntryType entryType,
                              String finalState, long amountCents, Instant recordedAt,
                              ArrayList<OrderTimestamp> timestamps) {
        LedgerEntry toEntry() {
            return new LedgerEntry(id, orderId, entryType, finalState,
                    timestamps, amountCents, recordedAt);
        }
    }
}
