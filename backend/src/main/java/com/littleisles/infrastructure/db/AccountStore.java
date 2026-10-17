package com.littleisles.infrastructure.db;

import com.littleisles.domain.reg.Account;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class AccountStore {
    private final String jdbcUrl;

    public AccountStore() {
        this(resolveDatabase());
    }

    public AccountStore(Path databaseFile) {
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("SQLite JDBC driver is not on the classpath.", exception);
        }
    }

    public long insert(Account account) throws SQLException {
        String sql = """
                INSERT INTO users (role, full_name, email, password_hash, phone)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.getRole().label());
            statement.setString(2, account.getFullName());
            statement.setString(3, account.getEmail());
            statement.setString(4, account.getPasswordHash());
            statement.setString(5, account.getPhone());
            statement.executeUpdate();
            try (PreparedStatement idStatement = connection.prepareStatement("SELECT last_insert_rowid()");
                 ResultSet keys = idStatement.executeQuery()) {
                if (!keys.next()) {
                    throw new SQLException("The new account id was not returned.");
                }
                return keys.getLong(1);
            }
        }
    }

    public static boolean isDuplicateEmail(SQLException exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("unique");
    }

    private static Path resolveDatabase() {
        Path[] candidates = {
                Path.of("database", "littleisle.sqlite"),
                Path.of("..", "database", "littleisle.sqlite"),
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("Could not find database/littleisle.sqlite.");
    }
}
