package com.littleisles.application.ntf;

import com.littleisles.application.ord.CityCatalog;
import com.littleisles.application.ord.OrderController;
import com.littleisles.application.prc.LedgerService;
import com.littleisles.application.prc.PricingService;
import com.littleisles.domain.ord.CityBounds;
import com.littleisles.domain.ord.CitySnapshot;
import com.littleisles.domain.ord.CourierPin;
import com.littleisles.domain.ord.CourierStatus;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.Product;
import com.littleisles.domain.ord.TransitionEvent;
import com.littleisles.domain.prc.PricingConfiguration;
import com.littleisles.domain.prc.StandardPricingStrategy;
import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountCredentials;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.Role;
import com.littleisles.infrastructure.db.LedgerStore;
import com.littleisles.infrastructure.db.OrderStore;
import com.littleisles.technicalservices.reg.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** NTF plugs into OrderController only through the OrderStatusListener extension point. */
class NotificationCenterTest {
    private static final String STORE_ID = "store-1";
    private static final String COURIER_PIN = "courier-1";
    private static final String ADDRESS = "1819 rue baile";
    private static final AuthenticatedAccount CUSTOMER =
            new AuthenticatedAccount(1, "Customer", "customer@example.com", "555-0100", Role.CUSTOMER, null);
    private static final AuthenticatedAccount GROCERIES =
            new AuthenticatedAccount(2, "Store", "store@example.com", "555-0101", Role.GROCERIES, null);
    private static final AuthenticatedAccount COURIER =
            new AuthenticatedAccount(3, "Courier", "courier@example.com", "555-0102", Role.COURIER, null);
    private static final AuthenticatedAccount OPERATOR =
            new AuthenticatedAccount(4, "Operator", "operator@example.com", "555-0103", Role.OPERATOR, null);
    private static final String LONG_AGO = "2026-01-01T00:00:00Z";

    @TempDir
    Path tempDirectory;

    private NotificationCenter center;
    private OrderController controller;

    @BeforeEach
    void setUp() throws SQLException {
        Path database = tempDirectory.resolve("orders.sqlite");
        createDatabase(database);
        GroceryStore store = new GroceryStore(STORE_ID, "Test Store", 45.50, -73.57);
        store.addProduct(new Product("rice", "Rice", 7.50));
        CityCatalog city = new CityCatalog(CitySnapshot.builder(new CityBounds(45.45, 45.56, -73.68, -73.40))
                .addGroceryStore(store)
                .addCourier(new CourierPin(COURIER_PIN, "Courier", 45.51, -73.56, CourierStatus.IDLE))
                .build());
        PricingService pricing = new PricingService(new StandardPricingStrategy(),
                new PricingConfiguration(299, 0, 150, 1500, 250));
        controller = new OrderController(city, new TestAccounts(), new OrderStore(database), pricing,
                new LedgerService(new LedgerStore(database)));
        controller.associateGroceriesAccount(GROCERIES.email(), STORE_ID);
        center = new NotificationCenter(Duration.ofSeconds(60));
        controller.addStatusListener(center);
    }

    @Test
    void customerHearsAboutEveryStatusChangeThroughTheExtensionPoint() throws SQLException {
        int id = placeOrder();
        controller.acceptPendingOrder(GROCERIES, id);
        controller.readyNextOrder(GROCERIES, id);
        controller.acceptCourierOffer(COURIER, id);
        controller.pickupCourierOrder(COURIER, id);
        controller.deliverCourierOrder(COURIER, id);
        controller.refundOrder(id);

        assertEquals(List.of(
                "Order #" + id + " was refunded.",
                "Order #" + id + " was delivered.",
                "Order #" + id + " was picked up and is on its way.",
                "Order #" + id + " has been assigned a courier.",
                "Order #" + id + " is ready and waiting for a courier.",
                "Order #" + id + " was accepted by Test Store and is being prepared.",
                "Order #" + id + " was placed at Test Store."), messages(CUSTOMER));
    }

    @Test
    void rejectionTellsTheCustomerWhy() throws SQLException {
        int id = placeOrder();

        controller.rejectPendingOrder(GROCERIES, id, "Out of stock");

        assertEquals("Order #" + id + " was rejected by Test Store. Reason: Out of stock", messages(CUSTOMER).get(0));
    }

    @Test
    void operatorIsNotAlertedBeforeTheThreshold() throws SQLException {
        int id = placeOrder();
        controller.acceptPendingOrder(GROCERIES, id);
        controller.readyNextOrder(GROCERIES, id);

        assertEquals(List.of(), messages(OPERATOR));
    }

    @Test
    void operatorIsAlertedOnceWhenAReadyOrderWaitsTooLong() {
        Order order = readySince(LONG_AGO);

        assertEquals(List.of("Order #" + order.getOrderId()
                + " at Test Store has waited over 60s in the dispatch queue."), messages(OPERATOR));
        assertEquals(1, messages(OPERATOR).size(), "a later poll must not repeat the alert");
        assertFalse(messages(CUSTOMER).stream().anyMatch(message -> message.contains("dispatch queue")));
    }

    @Test
    void noAlertOnceACourierHasAccepted() {
        Order order = readySince(LONG_AGO);

        center.orderStatusChanged(order, "accepted");

        assertEquals(List.of(), messages(OPERATOR));
    }

    @Test
    void clearForgetsNotificationsAndWaitingOrders() {
        readySince(LONG_AGO);

        center.clear();

        assertEquals(List.of(), messages(CUSTOMER));
        assertEquals(List.of(), messages(OPERATOR));
    }

    @Test
    void anonymousCallersAreRefused() {
        assertThrows(SecurityException.class, () -> center.forAccount(null));
    }

    private Order readySince(String timestamp) {
        Order order = new Order(new GroceryStore(STORE_ID, "Test Store", 45.50, -73.57), ADDRESS);
        order.setCustomerUserId(CUSTOMER.id());
        order.addTransitionEvent(TransitionEvent.restore("preparing", "ready", timestamp, null));
        center.orderStatusChanged(order, "ready");
        return order;
    }

    private int placeOrder() throws SQLException {
        return controller.placeOrder(CUSTOMER, STORE_ID, ADDRESS, Map.of("rice", 2), false).getOrderId();
    }

    private List<String> messages(AuthenticatedAccount account) {
        return center.forAccount(account).stream().map(NotificationCenter.Notification::message).toList();
    }

    private static void createDatabase(Path database) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
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
            if (CUSTOMER.email().equals(email)) {
                return new AccountCredentials(1, "customer", "Customer", email, "555-0100", "hash", null);
            }
            if (GROCERIES.email().equals(email)) {
                return new AccountCredentials(2, "groceries", "Store", email, "555-0101", "hash", null);
            }
            return null;
        }

        @Override
        public Long courierUserId(String courierPin) {
            return COURIER_PIN.equals(courierPin) ? COURIER.id() : null;
        }

        @Override
        public String courierPinForUser(long userId) {
            return userId == COURIER.id() ? COURIER_PIN : null;
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
    }
}
