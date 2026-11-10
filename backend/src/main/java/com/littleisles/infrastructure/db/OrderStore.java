package com.littleisles.infrastructure.db;

import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.OrderLine;
import com.littleisles.domain.ord.Product;

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
    }

    public int savePlacedOrder(long customerId, String storeId, String destination, Order order) throws SQLException {
        String orderSql = """
                INSERT INTO orders (
                    customer_id, groceries_id, status, delivery_address,
                    subtotal_cents, delivery_fee_cents, total_cents, created_at
                ) VALUES (?, ?, 'placed', ?, ?, 0, ?, datetime('now'))
                """;
        String lineSql = """
                INSERT INTO order_items (order_id, product_id, quantity, unit_price_cents)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            connection.setAutoCommit(false);
            try {
                long groceriesId = groceriesUserId(connection, storeId);
                int subtotalCents = 0;
                List<int[]> pricedLines = new ArrayList<>();
                for (OrderLine line : order.getOrderLines()) {
                    int unitPriceCents = Math.toIntExact(Math.round(line.getProduct().getPrice() * 100));
                    int productId = productId(connection, groceriesId, line.getProduct().getName(), unitPriceCents);
                    pricedLines.add(new int[] {productId, line.getQuantity(), unitPriceCents});
                    subtotalCents = Math.addExact(subtotalCents, Math.multiplyExact(unitPriceCents, line.getQuantity()));
                }
                int orderId;
                try (PreparedStatement statement = connection.prepareStatement(orderSql)) {
                    statement.setLong(1, customerId);
                    statement.setLong(2, groceriesId);
                    statement.setString(3, destination);
                    statement.setInt(4, subtotalCents);
                    statement.setInt(5, subtotalCents);
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

    public void assignCourier(int orderId, long courierUserId) throws SQLException {
        String sql = "UPDATE orders SET courier_id = ? WHERE id = ?";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, courierUserId);
            statement.setInt(2, orderId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("The courier could not be saved on the order.");
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

    public void delete(int orderId) throws SQLException {
        if (orderId <= 0) {
            throw new IllegalArgumentException("A saved order needs a positive id.");
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement items = connection.prepareStatement("DELETE FROM order_items WHERE order_id = ?")) {
                    items.setInt(1, orderId);
                    items.executeUpdate();
                }
                try (PreparedStatement orders = connection.prepareStatement("DELETE FROM orders WHERE id = ?")) {
                    orders.setInt(1, orderId);
                    if (orders.executeUpdate() != 1) {
                        throw new SQLException("The saved order could not be deleted.");
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public List<SavedOrder> loadAll() throws SQLException {
        String orderSql = """
                SELECT o.id, o.customer_id, gs.id AS store_id, o.delivery_address, o.status, o.courier_id
                FROM orders o
                JOIN users u ON u.id = o.groceries_id AND u.role = 'groceries'
                JOIN grocery_stores gs ON lower(u.store_code) = lower(gs.id)
                    OR upper(u.store_code) = upper(gs.store_code)
                WHERE o.status IN ('placed', 'accepted', 'preparing', 'ready', 'picked up', 'picked_up', 'on_the_way', 'delivered', 'cancelled')
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
                orders.getLong("courier_id");
                Long courierUserId = orders.wasNull() ? null : orders.getLong("courier_id");
                byId.put(id, new SavedOrder(
                        id,
                        orders.getLong("customer_id"),
                        orders.getString("store_id"),
                        orders.getString("delivery_address"),
                        domainStatus(orders.getString("status")),
                        courierUserId,
                        new ArrayList<>()));
            }
            try (Statement lineStatement = connection.createStatement();
                 ResultSet lines = lineStatement.executeQuery(lineSql)) {
                while (lines.next()) {
                    SavedOrder order = byId.get(lines.getInt("order_id"));
                    if (order == null) continue;
                    order.lines().add(new OrderLine(
                            new Product(Integer.toString(lines.getInt("product_id")), lines.getString("name"), lines.getInt("unit_price_cents") / 100.0),
                            lines.getInt("quantity")));
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
        if ("picked_up".equals(status)) {
            return "picked up";
        }
        return status;
    }

    private static String domainStatus(String status) {
        if ("cancelled".equals(status)) {
            return "canceled";
        }
        if ("picked up".equals(status) || "on_the_way".equals(status)) {
            return "picked_up";
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

    public record SavedOrder(int id, long customerId, String storeId, String destination, String status,
                             Long courierUserId, ArrayList<OrderLine> lines) {
    }
}
