package com.littleisles.infrastructure.db;

import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.OrderLine;
import com.littleisles.domain.ord.Product;
import com.littleisles.domain.prc.Fare;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrderStore {
    private final String jdbcUrl;

    public OrderStore() {
        this(resolveDatabase());
    }

    public OrderStore(Path databaseFile) {
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("SQLite JDBC driver is not on the classpath.", exception);
        }
        migrateFareColumns();
    }

    public int savePlacedOrder(long customerId, String storeId, String destination, Order order) throws SQLException {
        String orderSql = """
                INSERT INTO orders (
                    customer_id, groceries_id, status, delivery_address,
                    subtotal_cents, delivery_fee_cents, service_fee_cents,
                    small_order_surcharge_cents, total_cents, fare_version, created_at
                ) VALUES (?, ?, 'placed', ?, ?, ?, ?, ?, ?, 1, datetime('now'))
                """;
        String lineSql = """
                INSERT INTO order_items (order_id, product_id, quantity, unit_price_cents)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            connection.setAutoCommit(false);
            try {
                long groceriesId = groceriesUserId(connection, storeId);
                Fare fare = order.getFare();
                if (fare == null) {
                    throw new SQLException("The order must have a computed fare before it can be saved.");
                }
                List<int[]> pricedLines = new ArrayList<>();
                for (OrderLine line : order.getOrderLines()) {
                    int unitPriceCents = Math.toIntExact(line.getUnitPriceCents());
                    int productId = productId(connection, groceriesId, line.getProduct().getName(), unitPriceCents);
                    pricedLines.add(new int[] {productId, line.getQuantity(), unitPriceCents});
                }
                int orderId;
                try (PreparedStatement statement = connection.prepareStatement(orderSql)) {
                    statement.setLong(1, customerId);
                    statement.setLong(2, groceriesId);
                    statement.setString(3, destination);
                    statement.setLong(4, fare.subtotalCents());
                    statement.setLong(5, fare.deliveryFeeCents());
                    statement.setLong(6, fare.serviceFeeCents());
                    statement.setLong(7, fare.smallOrderSurchargeCents());
                    statement.setLong(8, fare.totalCents());
                    statement.executeUpdate();
                }
                try (PreparedStatement idStatement = connection.prepareStatement("SELECT last_insert_rowid()");
                     ResultSet keys = idStatement.executeQuery()) {
                    if (!keys.next()) {
                        throw new SQLException("The new order id was not returned.");
                    }
                    long id = keys.getLong(1);
                    if (id <= 0 || id > Integer.MAX_VALUE) {
                        throw new SQLException("The new order id is out of range.");
                    }
                    orderId = (int) id;
                }
                try (PreparedStatement lineStatement = connection.prepareStatement(lineSql)) {
                    for (int[] priced : pricedLines) {
                        lineStatement.setInt(1, orderId);
                        lineStatement.setInt(2, priced[0]);
                        lineStatement.setInt(3, priced[1]);
                        lineStatement.setInt(4, priced[2]);
                        lineStatement.executeUpdate();
                    }
                }
                connection.commit();
                return orderId;
            } catch (SQLException | ArithmeticException exception) {
                connection.rollback();
                if (exception instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new SQLException("The order total could not be saved.", exception);
            }
        }
    }

    public void updateStatus(int orderId, String status) throws SQLException {
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, databaseStatus(status));
            statement.setInt(2, orderId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("The saved order could not be updated.");
            }
        }
    }

    public void updateFare(int orderId, Fare fare) throws SQLException {
        String sql = "UPDATE orders SET subtotal_cents = ?, delivery_fee_cents = ?, service_fee_cents = ?, small_order_surcharge_cents = ?, total_cents = ?, fare_version = 1 WHERE id = ?";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, fare.subtotalCents());
            statement.setLong(2, fare.deliveryFeeCents());
            statement.setLong(3, fare.serviceFeeCents());
            statement.setLong(4, fare.smallOrderSurchargeCents());
            statement.setLong(5, fare.totalCents());
            statement.setInt(6, orderId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("The saved order fare could not be updated.");
            }
        }
    }

    public List<SavedOrder> loadAll() throws SQLException {
        String orderSql = """
                SELECT o.id, o.customer_id, gs.id AS store_id, o.delivery_address, o.status,
                       o.subtotal_cents, o.delivery_fee_cents, o.service_fee_cents,
                       o.small_order_surcharge_cents, o.total_cents, o.fare_version
                FROM orders o
                JOIN users u ON u.id = o.groceries_id AND u.role = 'groceries'
                JOIN grocery_stores gs ON lower(u.store_code) = lower(gs.id)
                    OR upper(u.store_code) = upper(gs.store_code)
                WHERE o.status IN ('placed', 'preparing', 'ready', 'cancelled', 'delivered')
                ORDER BY o.id
                """;
        String lineSql = """
                SELECT oi.order_id, p.id AS product_id, p.name, oi.quantity, oi.unit_price_cents
                FROM order_items oi
                JOIN products p ON p.id = oi.product_id
                ORDER BY oi.id
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement();
             ResultSet orders = statement.executeQuery(orderSql)) {
            Map<Integer, SavedOrder> byId = new LinkedHashMap<>();
            while (orders.next()) {
                int id = orders.getInt("id");
                byId.put(id, new SavedOrder(
                        id,
                        orders.getLong("customer_id"),
                        orders.getString("store_id"),
                        orders.getString("delivery_address"),
                        domainStatus(orders.getString("status")),
                        new Fare(orders.getLong("subtotal_cents"), orders.getLong("delivery_fee_cents"),
                                orders.getLong("service_fee_cents"), orders.getLong("small_order_surcharge_cents"),
                                orders.getLong("total_cents")),
                        orders.getInt("fare_version"),
                        new ArrayList<>()));
            }
            try (Statement lineStatement = connection.createStatement();
                 ResultSet lines = lineStatement.executeQuery(lineSql)) {
                while (lines.next()) {
                    SavedOrder order = byId.get(lines.getInt("order_id"));
                    if (order == null) continue;
                    order.lines().add(new OrderLine(
                            new Product(Integer.toString(lines.getInt("product_id")), lines.getString("name"), lines.getInt("unit_price_cents") / 100.0),
                            lines.getInt("quantity"),
                            lines.getLong("unit_price_cents")));
                }
            }
            return List.copyOf(byId.values());
        }
    }

    private static long groceriesUserId(Connection connection, String storeId) throws SQLException {
        String sql = """
                SELECT u.id
                FROM grocery_stores gs
                JOIN users u ON u.role = 'groceries'
                    AND (
                        lower(u.store_code) = lower(gs.id)
                        OR upper(u.store_code) = upper(gs.store_code)
                    )
                WHERE gs.id = ?
                ORDER BY u.id
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, storeId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getLong("id");
                }
            }
        }
        throw new SQLException("No groceries account is linked to this store.");
    }

    private static int productId(Connection connection, long groceriesId, String name, int priceCents) throws SQLException {
        String findSql = "SELECT id FROM products WHERE groceries_id = ? AND name = ? ORDER BY id LIMIT 1";
        try (PreparedStatement find = connection.prepareStatement(findSql)) {
            find.setLong(1, groceriesId);
            find.setString(2, name);
            try (ResultSet result = find.executeQuery()) {
                if (result.next()) {
                    return result.getInt("id");
                }
            }
        }
        String insertSql = """
                INSERT INTO products (groceries_id, name, description, price_cents, category, stock)
                VALUES (?, ?, '', ?, 'Grocery', 100)
                """;
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
            insert.setLong(1, groceriesId);
            insert.setString(2, name);
            insert.setInt(3, priceCents);
            insert.executeUpdate();
        }
        try (PreparedStatement idStatement = connection.prepareStatement("SELECT last_insert_rowid()");
             ResultSet keys = idStatement.executeQuery()) {
            if (!keys.next()) {
                throw new SQLException("The product id was not returned.");
            }
            return keys.getInt(1);
        }
    }

    private static String databaseStatus(String status) {
        if ("canceled".equals(status)) {
            return "cancelled";
        }
        return status;
    }

    private void migrateFareColumns() {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            boolean hasServiceFee = hasColumn(connection, "service_fee_cents");
            boolean hasSurcharge = hasColumn(connection, "small_order_surcharge_cents");
            boolean hasFareVersion = hasColumn(connection, "fare_version");
            if (hasServiceFee && hasSurcharge && hasFareVersion) return;

            statement.execute("PRAGMA foreign_keys = OFF");
            connection.setAutoCommit(false);
            try {
                statement.execute("""
                        CREATE TABLE orders_with_fare (
                            id INTEGER PRIMARY KEY,
                            customer_id INTEGER NOT NULL REFERENCES users (id),
                            groceries_id INTEGER NOT NULL REFERENCES users (id),
                            courier_id INTEGER REFERENCES users (id),
                            status TEXT NOT NULL CHECK (status IN ('placed', 'accepted', 'preparing', 'ready', 'picked_up', 'on_the_way', 'delivered', 'cancelled')),
                            delivery_address TEXT NOT NULL,
                            subtotal_cents INTEGER NOT NULL CHECK (subtotal_cents >= 0),
                            delivery_fee_cents INTEGER NOT NULL CHECK (delivery_fee_cents >= 0),
                            service_fee_cents INTEGER NOT NULL DEFAULT 0 CHECK (service_fee_cents >= 0),
                            small_order_surcharge_cents INTEGER NOT NULL DEFAULT 0 CHECK (small_order_surcharge_cents >= 0),
                            total_cents INTEGER NOT NULL CHECK (total_cents = subtotal_cents + delivery_fee_cents + service_fee_cents + small_order_surcharge_cents),
                            fare_version INTEGER NOT NULL DEFAULT 1 CHECK (fare_version >= 0),
                            created_at TEXT NOT NULL
                        )
                        """);
                String serviceFee = hasServiceFee ? "service_fee_cents" : "0";
                String surcharge = hasSurcharge ? "small_order_surcharge_cents" : "0";
                String fareVersion = hasFareVersion
                        ? "fare_version"
                        : "CASE WHEN delivery_fee_cents = 0 AND total_cents = subtotal_cents THEN 0 ELSE 1 END";
                statement.execute("""
                        INSERT INTO orders_with_fare (
                            id, customer_id, groceries_id, courier_id, status, delivery_address,
                            subtotal_cents, delivery_fee_cents, service_fee_cents,
                            small_order_surcharge_cents, total_cents, fare_version, created_at
                        )
                        SELECT id, customer_id, groceries_id, courier_id, status, delivery_address,
                               subtotal_cents, delivery_fee_cents, %s, %s, total_cents, %s, created_at
                        FROM orders
                        """.formatted(serviceFee, surcharge, fareVersion));
                statement.execute("DROP TABLE orders");
                statement.execute("ALTER TABLE orders_with_fare RENAME TO orders");
                statement.execute("""
                        CREATE TRIGGER orders_party_roles
                        BEFORE INSERT ON orders
                        FOR EACH ROW
                        WHEN (SELECT role FROM users WHERE id = NEW.customer_id) IS NOT 'customer'
                          OR (SELECT role FROM users WHERE id = NEW.groceries_id) IS NOT 'groceries'
                          OR (NEW.courier_id IS NOT NULL
                              AND (SELECT role FROM users WHERE id = NEW.courier_id) IS NOT 'courier')
                        BEGIN
                            SELECT RAISE(ABORT, 'order parties must match their roles');
                        END
                        """);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
                statement.execute("PRAGMA foreign_keys = ON");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Order fare columns could not be prepared.", exception);
        }
    }

    private static boolean hasColumn(Connection connection, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(orders)")) {
            while (columns.next()) {
                if (columnName.equals(columns.getString("name"))) return true;
            }
        }
        return false;
    }

    private static String domainStatus(String status) {
        if ("cancelled".equals(status)) {
            return "canceled";
        }
        return status;
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

    public record SavedOrder(int id, long customerId, String storeId, String destination, String status, Fare fare,
                             int fareVersion,
                             ArrayList<OrderLine> lines) {
    }
}
