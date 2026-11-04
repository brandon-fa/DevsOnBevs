package com.littleisles.infrastructure.db;

import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountCredentials;
import com.littleisles.domain.reg.CustomerAccount;
import com.littleisles.domain.reg.GroceriesAccount;
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
        ensureStoreCodes();
    }

    public long insert(Account account) throws SQLException {
        String sql = """
                INSERT INTO users (role, full_name, email, password_hash, phone, store_code, default_address)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            connection.setAutoCommit(false);
            long id;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindUser(statement, account);
                statement.executeUpdate();
            }
            try (PreparedStatement idStatement = connection.prepareStatement("SELECT last_insert_rowid()");
                 ResultSet keys = idStatement.executeQuery()) {
                if (!keys.next()) {
                    throw new SQLException("The new account id was not returned.");
                }
                id = keys.getLong(1);
            }
            connection.commit();
            return id;
        }
    }

    @Override
    public void saveSeededAccount(Account account) throws SQLException {
        String findSql = "SELECT id FROM users WHERE lower(email) = lower(?) ORDER BY id LIMIT 1";
        String updateSql = "UPDATE users SET role = ?, full_name = ?, email = ?, password_hash = ?, phone = ?, store_code = ?, default_address = ? WHERE id = ?";
        String insertSql = "INSERT INTO users (role, full_name, email, password_hash, phone, store_code, default_address) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement find = connection.prepareStatement(findSql)) {
            find.setString(1, account.getEmail());
            Long existingId = null;
            try (ResultSet result = find.executeQuery()) {
                if (result.next()) existingId = result.getLong("id");
            }
            connection.setAutoCommit(false);
            long userId;
            if (existingId != null) {
                userId = existingId;
                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    bindUser(update, account);
                    update.setLong(8, existingId);
                    update.executeUpdate();
                }
            } else {
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    bindUser(insert, account);
                    insert.executeUpdate();
                }
                try (PreparedStatement idStatement = connection.prepareStatement("SELECT last_insert_rowid()");
                     ResultSet keys = idStatement.executeQuery()) {
                    if (!keys.next()) {
                        throw new SQLException("The new account id was not returned.");
                    }
                    userId = keys.getLong(1);
                }
            }
            connection.commit();
        }
    }

    private static void bindUser(PreparedStatement statement, Account account) throws SQLException {
        statement.setString(1, account.getRole().label());
        statement.setString(2, account.getFullName());
        statement.setString(3, account.getEmail());
        statement.setString(4, account.getPasswordHash());
        statement.setString(5, account.getPhone());
        statement.setString(6, account instanceof GroceriesAccount groceries ? groceries.getStoreCode() : null);
        statement.setString(7, account instanceof CustomerAccount customer ? customer.getDefaultAddress() : null);
    }

    @Override
    public void updatePhoneAndPassword(long id, String phone, String passwordHash, String currentPasswordHash, String defaultAddress, boolean updateAddress) throws SQLException {
        boolean changePassword = passwordHash != null;
        String addressSql = updateAddress ? ", default_address = ?" : "";
        String sql = changePassword
                ? "UPDATE users SET phone = ?, password_hash = ?" + addressSql + " WHERE id = ? AND password_hash = ?"
                : "UPDATE users SET phone = ?" + addressSql + " WHERE id = ?";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, phone);
            if (changePassword) statement.setString(index++, passwordHash);
            if (updateAddress) statement.setString(index++, defaultAddress);
            statement.setLong(index++, id);
            if (changePassword) statement.setString(index, currentPasswordHash);
            if (statement.executeUpdate() != 1) {
                if (changePassword) {
                    throw new IllegalArgumentException("Current password is incorrect.");
                }
                throw new SQLException("The account could not be updated.");
            }
        }
    }

    @Override
    public void saveDefaultAddress(long id, String defaultAddress) throws SQLException {
        String sql = "UPDATE users SET default_address = ? WHERE id = ?";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (defaultAddress == null || defaultAddress.isBlank()) {
                statement.setNull(1, java.sql.Types.VARCHAR);
            } else {
                statement.setString(1, defaultAddress.trim());
            }
            statement.setLong(2, id);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("The default address could not be saved.");
            }
        }
    }

    @Override
    public AccountCredentials findByEmail(String email) throws SQLException {
        String sql = "SELECT id, role, full_name, email, phone, password_hash, default_address FROM users WHERE lower(email) = lower(?)";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email.trim());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return new AccountCredentials(result.getLong("id"), result.getString("role"),
                        result.getString("full_name"), result.getString("email"),
                        result.getString("phone"), result.getString("password_hash"),
                        result.getString("default_address"));
            }
        }
    }


    public boolean storeCodeExists(String storeCode) throws SQLException {
        if (storeCode == null || storeCode.isBlank()) return false;
        String sql = "SELECT 1 FROM grocery_stores WHERE upper(store_code) = upper(?)";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, storeCode.trim());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void ensureStoreCodes() {
        String[][] stores = {
                {"olive-rye", "Olive & Rye", "OLIVE"},
                {"island-pantry", "Island Pantry", "ISLAND"},
                {"spice-route", "Spice Route", "SPICE"},
                {"cedar-basket", "Cedar Basket", "CEDAR"},
                {"pearl-market", "Pearl Market", "PEARL"},
        };
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS grocery_stores (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        store_code TEXT NOT NULL UNIQUE
                    )
                    """);
            boolean hasStoreCode = false;
            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(users)")) {
                while (columns.next()) {
                    if ("store_code".equalsIgnoreCase(columns.getString("name"))) hasStoreCode = true;
                }
            }
            if (!hasStoreCode) {
                statement.execute("ALTER TABLE users ADD COLUMN store_code TEXT");
            }
            boolean hasDefaultAddress = false;
            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(users)")) {
                while (columns.next()) {
                    if ("default_address".equalsIgnoreCase(columns.getString("name"))) hasDefaultAddress = true;
                }
            }
            if (!hasDefaultAddress) {
                statement.execute("ALTER TABLE users ADD COLUMN default_address TEXT");
            }
            try (PreparedStatement upsert = connection.prepareStatement(
                    "INSERT INTO grocery_stores (id, name, store_code) VALUES (?, ?, ?) ON CONFLICT(id) DO UPDATE SET name = excluded.name, store_code = excluded.store_code")) {
                for (String[] store : stores) {
                    upsert.setString(1, store[0]);
                    upsert.setString(2, store[1]);
                    upsert.setString(3, store[2]);
                    upsert.executeUpdate();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Grocery store codes could not be prepared.", exception);
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
