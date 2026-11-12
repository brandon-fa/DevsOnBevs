package com.littleisles.application.prc;

import com.littleisles.application.ord.CityCatalog;
import com.littleisles.application.ord.OrderController;
import com.littleisles.domain.ord.CityBounds;
import com.littleisles.domain.ord.CitySnapshot;
import com.littleisles.domain.ord.CourierPin;
import com.littleisles.domain.ord.CourierStatus;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.ord.Product;
import com.littleisles.domain.prc.LedgerEntry;
import com.littleisles.domain.prc.PricingConfiguration;
import com.littleisles.domain.prc.StandardPricingStrategy;
import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountCredentials;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.Role;
import com.littleisles.infrastructure.db.LedgerStore;
import com.littleisles.infrastructure.db.OrderStore;
import com.littleisles.technicalservices.reg.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderLedgerIntegrationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void completingDeliveryPersistsAndReloadsItsLedgerEntry() throws Exception {
        Path database = tempDirectory.resolve("completed-order.sqlite");
        createDatabase(database);
        OrderStore orderStore = new OrderStore(database);
        LedgerStore ledgerStore = new LedgerStore(database);
        LedgerService ledgerService = new LedgerService(ledgerStore);

        GroceryStore store = new GroceryStore("store-1", "Test Store", 45.50, -73.57);
        store.addProduct(new Product("rice", "Rice", 7.50));
        CityCatalog city = new CityCatalog(CitySnapshot.builder(
                        new CityBounds(45.45, 45.56, -73.68, -73.40))
                .addGroceryStore(store)
                .addCourier(new CourierPin("courier-1", "Test Courier", 45.50, -73.57, CourierStatus.IDLE))
                .build());
        AccountRepository accounts = new TestAccounts();
        PricingService pricing = new PricingService(new StandardPricingStrategy(),
                new PricingConfiguration(299, 0, 150, 1500, 250));
        OrderController controller = new OrderController(city, accounts, orderStore, pricing, ledgerService);
        controller.associateGroceriesAccount("store@example.com", "store-1");

        AuthenticatedAccount customer = account(1, "customer@example.com", Role.CUSTOMER);
        AuthenticatedAccount groceries = account(2, "store@example.com", Role.GROCERIES);
        AuthenticatedAccount courier = account(3, "courier@example.com", Role.COURIER);

        int orderId = controller.placeOrder(customer, "store-1", "1819 rue baile",
                Map.of("rice", 2), false).getOrderId();
        controller.acceptPendingOrder(groceries, orderId);
        controller.readyNextOrder(groceries, orderId);
        controller.acceptCourierOffer(courier, orderId);
        controller.pickupCourierOrder(courier, orderId);
        controller.deliverCourierOrder(courier, orderId);

        LedgerEntry saved = new LedgerService(new LedgerStore(database)).entries().get(0);
        assertEquals(orderId, saved.orderId());
        assertEquals("delivered", saved.finalState());
        assertEquals(1_949, saved.amountCents());
        assertEquals(List.of("placed", "preparing", "ready", "picked_up", "delivered"),
                saved.timestamps().stream().map(timestamp -> timestamp.state()).toList());
    }

    private static AuthenticatedAccount account(long id, String email, Role role) {
        return new AuthenticatedAccount(id, role.label(), email, "555-0100", role, null);
    }

    private static void createDatabase(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, role TEXT NOT NULL, store_code TEXT)");
            statement.execute("CREATE TABLE grocery_stores (id TEXT PRIMARY KEY, store_code TEXT NOT NULL)");
            statement.execute("CREATE TABLE products (id INTEGER PRIMARY KEY, groceries_id INTEGER, name TEXT, description TEXT, price_cents INTEGER, category TEXT, stock INTEGER)");
            statement.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, customer_id INTEGER NOT NULL, groceries_id INTEGER NOT NULL, courier_id INTEGER, status TEXT NOT NULL CHECK (status IN ('placed', 'accepted', 'preparing', 'ready', 'picked_up', 'on_the_way', 'delivered', 'cancelled')), delivery_address TEXT NOT NULL, subtotal_cents INTEGER NOT NULL, delivery_fee_cents INTEGER NOT NULL, service_fee_cents INTEGER NOT NULL DEFAULT 0, small_order_surcharge_cents INTEGER NOT NULL DEFAULT 0, total_cents INTEGER NOT NULL, fare_version INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL)");
            statement.execute("CREATE TABLE order_items (id INTEGER PRIMARY KEY, order_id INTEGER NOT NULL REFERENCES orders (id), product_id INTEGER NOT NULL REFERENCES products (id), quantity INTEGER NOT NULL, unit_price_cents INTEGER NOT NULL)");
            statement.execute("INSERT INTO users VALUES (1, 'customer', NULL), (2, 'groceries', 'STORE-CODE'), (3, 'courier', NULL)");
            statement.execute("INSERT INTO grocery_stores VALUES ('store-1', 'store-code')");
        }
    }

    private static final class TestAccounts implements AccountRepository {
        @Override
        public AccountCredentials findByEmail(String email) {
            if ("customer@example.com".equals(email)) {
                return new AccountCredentials(1, "customer", "Customer", email, "555-0100", "hash", null);
            }
            if ("store@example.com".equals(email)) {
                return new AccountCredentials(2, "groceries", "Store", email, "555-0101", "hash", null);
            }
            return null;
        }

        @Override
        public void saveSeededAccount(Account account) {
        }

        @Override
        public void updatePhoneAndPassword(long id, String phone, String passwordHash,
                                           String currentPasswordHash, String defaultAddress,
                                           boolean updateAddress) {
        }

        @Override
        public void saveDefaultAddress(long id, String defaultAddress) {
        }

        @Override
        public Long courierUserId(String courierPin) {
            return "courier-1".equals(courierPin) ? 3L : null;
        }

        @Override
        public String courierPinForUser(long userId) {
            return userId == 3 ? "courier-1" : null;
        }

        @Override
        public String courierStatusForPin(String courierPin) {
            return "idle";
        }
    }
}
