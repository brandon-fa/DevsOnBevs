package com.littleisles.infrastructure.db;

import com.littleisles.domain.ord.CourierPin;
import com.littleisles.domain.ord.CourierRosterEntry;
import com.littleisles.domain.ord.CourierStatus;
import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountCredentials;
import com.littleisles.domain.reg.CourierAccount;
import com.littleisles.domain.reg.CustomerAccount;
import com.littleisles.domain.reg.GroceriesAccount;
import com.littleisles.technicalservices.reg.AccountRepository;

import java.util.ArrayList;
import java.util.List;
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
                INSERT INTO users (role, full_name, email, password_hash, phone, store_code, default_address, courier_pin)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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
        String updateSql = "UPDATE users SET role = ?, full_name = ?, email = ?, password_hash = ?, phone = ?, store_code = ?, default_address = ?, courier_pin = ? WHERE id = ?";
        String insertSql = "INSERT INTO users (role, full_name, email, password_hash, phone, store_code, default_address, courier_pin) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
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
                    update.setLong(9, existingId);
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
        String storeCode = account instanceof GroceriesAccount groceries ? groceries.getStoreCode() : null;
        statement.setString(6, storeCode);
        statement.setString(7, account instanceof CustomerAccount customer ? customer.getDefaultAddress() : null);
        statement.setString(8, account instanceof CourierAccount courier ? courier.getCourierPin() : null);
    }

    @Override
    public Long courierUserId(String courierPin) throws SQLException {
        if (courierPin == null || courierPin.isBlank()) return null;
        String sql = "SELECT id FROM users WHERE role = 'courier' AND lower(courier_pin) = lower(?) ORDER BY id LIMIT 1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, courierPin.trim());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong("id") : null;
            }
        }
    }

    @Override
    public String courierPinForUser(long userId) throws SQLException {
        String sql = "SELECT courier_pin FROM users WHERE id = ? AND role = 'courier'";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return result.getString("courier_pin");
            }
        }
    }

    @Override
    public String registrationStoreCode(long userId) throws SQLException {
        return groceryStoreField(userId, "store_code");
    }

    @Override
    public String linkedStoreId(long userId) throws SQLException {
        return groceryStoreField(userId, "id");
    }

    @Override
    public List<CourierRosterEntry> listCouriers() throws SQLException {
        String sql = """
                SELECT id, full_name, courier_pin, courier_status
                FROM users
                WHERE role = 'courier'
                ORDER BY full_name, id
                """;
        List<CourierRosterEntry> couriers = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String statusLabel = result.getString("courier_status");
                String status = statusLabel == null || statusLabel.isBlank()
                        ? CourierStatus.IDLE.label()
                        : statusLabel;
                couriers.add(new CourierRosterEntry(
                        result.getLong("id"),
                        result.getString("full_name"),
                        result.getString("courier_pin"),
                        status));
            }
        }
        return couriers;
    }

    public List<CourierPin> couriersWithLocation() throws SQLException {
        String sql = """
                SELECT id, full_name, courier_pin, courier_status, latitude, longitude
                FROM users
                WHERE role = 'courier' AND latitude IS NOT NULL AND longitude IS NOT NULL
                ORDER BY id
                """;
        List<CourierPin> pins = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String pin = result.getString("courier_pin");
                String id = pin == null || pin.isBlank() ? "courier-" + result.getLong("id") : pin.trim();
                String statusLabel = result.getString("courier_status");
                CourierStatus status = statusLabel == null || statusLabel.isBlank()
                        ? CourierStatus.IDLE
                        : CourierStatus.fromLabel(statusLabel);
                pins.add(new CourierPin(id, result.getString("full_name"),
                        result.getDouble("latitude"), result.getDouble("longitude"), status));
            }
        }
        return pins;
    }

    @Override
    public String courierStatusForPin(String courierPin) throws SQLException {
        if (courierPin == null || courierPin.isBlank()) return null;
        String sql = "SELECT courier_status FROM users WHERE role = 'courier' AND lower(courier_pin) = lower(?) ORDER BY id LIMIT 1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, courierPin.trim());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("courier_status") : null;
            }
        }
    }

    @Override
    public void updateCourierStatus(long userId, String status) throws SQLException {
        String sql = "UPDATE users SET courier_status = ? WHERE id = ? AND role = 'courier'";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setLong(2, userId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("The courier status could not be saved.");
            }
        }
    }

    @Override
    public void updateCourierLocation(long userId, double lat, double lon) throws SQLException {
        String sql = "UPDATE users SET latitude = ?, longitude = ? WHERE id = ? AND role = 'courier'";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, lat);
            statement.setDouble(2, lon);
            statement.setLong(3, userId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("The courier location could not be saved.");
            }
        }
    }

    @Override
    public List<AccountCredentials> listCustomerAccounts() throws SQLException {
        String sql = """
                SELECT id, role, full_name, email, phone, password_hash, default_address
                FROM users
                WHERE role = 'customer'
                ORDER BY id
                """;
        List<AccountCredentials> accounts = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                accounts.add(new AccountCredentials(
                        result.getLong("id"),
                        result.getString("role"),
                        result.getString("full_name"),
                        result.getString("email"),
                        result.getString("phone"),
                        result.getString("password_hash"),
                        result.getString("default_address")));
            }
        }
        return accounts;
    }

    private String groceryStoreField(long userId, String column) throws SQLException {
        String sql = """
                SELECT gs.id, gs.store_code
                FROM users u
                JOIN grocery_stores gs ON lower(u.store_code) = lower(gs.id)
                    OR upper(u.store_code) = upper(gs.store_code)
                WHERE u.id = ? AND u.role = 'groceries'
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return result.getString(column);
            }
        }
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
            boolean hasCourierPin = false;
            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(users)")) {
                while (columns.next()) {
                    if ("courier_pin".equalsIgnoreCase(columns.getString("name"))) hasCourierPin = true;
                }
            }
            if (!hasCourierPin) {
                statement.execute("ALTER TABLE users ADD COLUMN courier_pin TEXT");
            }
            boolean hasCourierStatus = false;
            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(users)")) {
                while (columns.next()) {
                    if ("courier_status".equalsIgnoreCase(columns.getString("name"))) hasCourierStatus = true;
                }
            }
            if (!hasCourierStatus) {
                statement.execute("ALTER TABLE users ADD COLUMN courier_status TEXT");
            }
            boolean hasLatitude = false;
            boolean hasLongitude = false;
            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(users)")) {
                while (columns.next()) {
                    String name = columns.getString("name");
                    if ("latitude".equalsIgnoreCase(name)) hasLatitude = true;
                    if ("longitude".equalsIgnoreCase(name)) hasLongitude = true;
                }
            }
            if (!hasLatitude) {
                statement.execute("ALTER TABLE users ADD COLUMN latitude REAL");
            }
            if (!hasLongitude) {
                statement.execute("ALTER TABLE users ADD COLUMN longitude REAL");
            }
            try (PreparedStatement place = connection.prepareStatement(
                    "UPDATE users SET latitude = ?, longitude = ? WHERE lower(email) = lower(?) AND role = 'courier' AND latitude IS NULL")) {
                place.setDouble(1, 45.5210);
                place.setDouble(2, -73.5850);
                place.setString(3, "demo.courier@littleisles.example");
                place.executeUpdate();
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
