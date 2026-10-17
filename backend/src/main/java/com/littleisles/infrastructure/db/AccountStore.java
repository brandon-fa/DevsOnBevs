package com.littleisles.infrastructure.db;

import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountCredentials;
import com.littleisles.technicalservices.reg.AccountRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class AccountStore implements AccountRepository {
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

    @Override
    public void saveSeededAccount(Account account) throws SQLException {
        String findSql = "SELECT id FROM users WHERE lower(email) = lower(?) ORDER BY id LIMIT 1";
        String updateSql = "UPDATE users SET role = ?, full_name = ?, email = ?, password_hash = ?, phone = ? WHERE id = ?";
        String insertSql = "INSERT INTO users (role, full_name, email, password_hash, phone) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement find = connection.prepareStatement(findSql)) {
            find.setString(1, account.getEmail());
            Long existingId = null;
            try (ResultSet result = find.executeQuery()) {
                if (result.next()) existingId = result.getLong("id");
            }
            if (existingId != null) {
                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    update.setString(1, account.getRole().label());
                    update.setString(2, account.getFullName());
                    update.setString(3, account.getEmail());
                    update.setString(4, account.getPasswordHash());
                    update.setString(5, account.getPhone());
                    update.setLong(6, existingId);
                    update.executeUpdate();
                }
            } else {
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    insert.setString(1, account.getRole().label());
                    insert.setString(2, account.getFullName());
                    insert.setString(3, account.getEmail());
                    insert.setString(4, account.getPasswordHash());
                    insert.setString(5, account.getPhone());
                    insert.executeUpdate();
                }
            }
        }
    }

    @Override
    public AccountCredentials findByEmail(String email) throws SQLException {
        String sql = "SELECT id, role, full_name, email, phone, password_hash FROM users WHERE lower(email) = lower(?)";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email.trim());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return new AccountCredentials(result.getLong("id"), result.getString("role"),
                        result.getString("full_name"), result.getString("email"),
                        result.getString("phone"), result.getString("password_hash"));
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
