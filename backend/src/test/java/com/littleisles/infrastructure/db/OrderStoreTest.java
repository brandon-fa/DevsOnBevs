package com.littleisles.infrastructure.db;

import com.littleisles.application.ord.CityCatalog;
import com.littleisles.application.ord.OrderController;
import com.littleisles.application.prc.PricingService;
import com.littleisles.domain.ord.CityBounds;
import com.littleisles.domain.ord.CitySnapshot;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.Product;
import com.littleisles.domain.prc.Fare;
import com.littleisles.domain.prc.PricingConfiguration;
import com.littleisles.domain.prc.StandardPricingStrategy;
import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountCredentials;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.Role;
import com.littleisles.technicalservices.reg.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void migratesLegacyOrdersAndPersistsCompleteFareForNewOrders() throws Exception {
        Path database = tempDir.resolve("orders.sqlite");
        createLegacyDatabase(database);

        OrderStore store = new OrderStore(database);
        assertLegacyOrderPreserved(database);

        GroceryStore groceryStore = new GroceryStore("store-1", "Test Store", 0, 0);
        Product product = new Product("product-1", "Rice", 7.50);
        groceryStore.addProduct(product);
        Order order = new Order(groceryStore, "1 Test Street");
        order.addOrderLine(product, 2);
        order.assignFare(new Fare(1500, 400, 120, 250, 2270));

        int id = store.savePlacedOrder(1, "store-1", order.getDestination(), order);
        OrderStore.SavedOrder saved = store.loadAll().stream()
                .filter(candidate -> candidate.id() == id)
                .findFirst()
                .orElseThrow();

        assertEquals(order.getFare(), saved.fare());
        assertEquals(1, saved.lines().size());
        assertStoredFare(database, id);
        assertInvalidTotalRejected(database);
    }

    @Test
    void orderPlacementCalculatesAndSavesTheConfiguredFare() throws Exception {
        Path database = tempDir.resolve("placement.sqlite");
        createLegacyDatabase(database);
        OrderStore store = new OrderStore(database);
        GroceryStore groceryStore = new GroceryStore("store-1", "Test Store", 0, 0);
        groceryStore.addProduct(new Product("rice", "Rice", 7.50));
        CityCatalog cityCatalog = new CityCatalog(CitySnapshot.builder(new CityBounds(-1, 1, -1, 1))
                .addGroceryStore(groceryStore)
                .build());
        AccountRepository accounts = new AccountRepository() {
            @Override
            public AccountCredentials findByEmail(String email) {
                return new AccountCredentials(1, "customer", "Test Customer", email, "555-0100", "hash", null);
            }

            @Override
            public void saveSeededAccount(Account account) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void updatePhoneAndPassword(long id, String phone, String passwordHash,
                                               String currentPasswordHash, String defaultAddress,
                                               boolean updateAddress) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void saveDefaultAddress(long id, String defaultAddress) {
                throw new UnsupportedOperationException();
            }
        };
        PricingService pricing = new PricingService(new StandardPricingStrategy(),
                new PricingConfiguration(400, 0, 120, 2000, 250));
        OrderController controller = new OrderController(cityCatalog, accounts, store, pricing);

        Order placed = controller.placeOrder(
                new AuthenticatedAccount(1, "Test Customer", "test@example.com", "555-0100", Role.CUSTOMER, null),
                "store-1", "1 Test Street", Map.of("rice", 2), false);

        Fare expected = new Fare(1500, 400, 120, 250, 2270);
        assertEquals(expected, placed.getFare());
        assertEquals(expected, store.loadAll().stream()
                .filter(saved -> saved.id() == placed.getOrderId())
                .findFirst()
                .orElseThrow()
                .fare());
    }

    @Test
    void repairsLegacySubtotalOnlyOrdersUsingConfiguredPricingOnce() throws Exception {
        Path database = tempDir.resolve("legacy-orders.sqlite");
        createLegacyDatabase(database);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO products VALUES (2, 2, 'Bugged item', '', 300, 'Grocery', 1)");
            statement.execute("INSERT INTO orders VALUES (43, 1, 2, NULL, 'placed', 'Legacy Address', 300, 0, 300, '2026-01-02')");
            statement.execute("INSERT INTO order_items VALUES (2, 43, 2, 1, 300)");
        }

        OrderStore store = new OrderStore(database);
        GroceryStore groceryStore = new GroceryStore("store-1", "Test Store", 0, 0);
        groceryStore.addProduct(new Product("old-item", "Old item", 5.00));
        groceryStore.addProduct(new Product("bugged-item", "Bugged item", 3.00));
        CityCatalog cityCatalog = new CityCatalog(CitySnapshot.builder(new CityBounds(-1, 1, -1, 1))
                .addGroceryStore(groceryStore)
                .build());
        AccountRepository accounts = new AccountRepository() {
            @Override
            public AccountCredentials findByEmail(String email) {
                return new AccountCredentials(1, "customer", "Test Customer", email, "555-0100", "hash", null);
            }

            @Override
            public void saveSeededAccount(Account account) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void updatePhoneAndPassword(long id, String phone, String passwordHash,
                                               String currentPasswordHash, String defaultAddress,
                                               boolean updateAddress) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void saveDefaultAddress(long id, String defaultAddress) {
                throw new UnsupportedOperationException();
            }
        };
        PricingService pricing = new PricingService(new StandardPricingStrategy(),
                new PricingConfiguration(400, 0, 120, 2000, 250));
        new OrderController(cityCatalog, accounts, store, pricing);

        OrderStore.SavedOrder repaired = store.loadAll().stream()
                .filter(saved -> saved.id() == 43)
                .findFirst()
                .orElseThrow();
        assertEquals(new Fare(300, 400, 120, 250, 1070), repaired.fare());
        assertEquals(1, repaired.fareVersion());
    }

    private static void createLegacyDatabase(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, role TEXT NOT NULL, store_code TEXT)");
            statement.execute("CREATE TABLE grocery_stores (id TEXT PRIMARY KEY, store_code TEXT NOT NULL)");
            statement.execute("CREATE TABLE products (id INTEGER PRIMARY KEY, groceries_id INTEGER, name TEXT, description TEXT, price_cents INTEGER, category TEXT, stock INTEGER)");
            statement.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, customer_id INTEGER NOT NULL, groceries_id INTEGER NOT NULL, courier_id INTEGER, status TEXT NOT NULL CHECK (status IN ('placed', 'accepted', 'preparing', 'ready', 'picked_up', 'on_the_way', 'delivered', 'cancelled')), delivery_address TEXT NOT NULL, subtotal_cents INTEGER NOT NULL CHECK (subtotal_cents >= 0), delivery_fee_cents INTEGER NOT NULL CHECK (delivery_fee_cents >= 0), total_cents INTEGER NOT NULL CHECK (total_cents = subtotal_cents + delivery_fee_cents), created_at TEXT NOT NULL)");
            statement.execute("CREATE TABLE order_items (id INTEGER PRIMARY KEY, order_id INTEGER NOT NULL REFERENCES orders (id), product_id INTEGER NOT NULL REFERENCES products (id), quantity INTEGER NOT NULL, unit_price_cents INTEGER NOT NULL)");
            statement.execute("INSERT INTO users VALUES (1, 'customer', NULL), (2, 'groceries', 'STORE-CODE')");
            statement.execute("INSERT INTO grocery_stores VALUES ('store-1', 'store-code')");
            statement.execute("INSERT INTO products VALUES (1, 2, 'Old item', '', 500, 'Grocery', 1)");
            statement.execute("INSERT INTO orders VALUES (42, 1, 2, NULL, 'delivered', 'Old Address', 1000, 300, 1300, '2026-01-01')");
            statement.execute("INSERT INTO order_items VALUES (1, 42, 1, 2, 500)");
        }
    }

    private static void assertLegacyOrderPreserved(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT subtotal_cents, delivery_fee_cents, total_cents, service_fee_cents, small_order_surcharge_cents FROM orders WHERE id = 42")) {
            assertTrue(result.next());
            assertEquals(1000, result.getLong("subtotal_cents"));
            assertEquals(300, result.getLong("delivery_fee_cents"));
            assertEquals(1300, result.getLong("total_cents"));
            assertEquals(0, result.getLong("service_fee_cents"));
            assertEquals(0, result.getLong("small_order_surcharge_cents"));
        }
    }

    private static void assertStoredFare(Path database, int id) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT subtotal_cents, delivery_fee_cents, service_fee_cents, small_order_surcharge_cents, total_cents FROM orders WHERE id = " + id)) {
            assertTrue(result.next());
            assertEquals(1500, result.getLong("subtotal_cents"));
            assertEquals(400, result.getLong("delivery_fee_cents"));
            assertEquals(120, result.getLong("service_fee_cents"));
            assertEquals(250, result.getLong("small_order_surcharge_cents"));
            assertEquals(2270, result.getLong("total_cents"));
        }
    }

    private static void assertInvalidTotalRejected(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO orders (customer_id, groceries_id, status, delivery_address, subtotal_cents, delivery_fee_cents, service_fee_cents, small_order_surcharge_cents, total_cents, created_at) VALUES (1, 2, 'placed', 'Wrong total', 1500, 400, 120, 250, 2271, '2026-01-01')"));
            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM order_items WHERE order_id = 42")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }
}
