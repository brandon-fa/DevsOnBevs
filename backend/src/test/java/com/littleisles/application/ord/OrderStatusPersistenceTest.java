package com.littleisles.application.ord;

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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R4 characterisation: what each order state change in OrderController saves today
 * (status column, transition events, courier, ledger), pinned before the state changes
 * are routed through a single method.
 */
class OrderStatusPersistenceTest {
    private static final String STORE_ID = "store-1";
    private static final String COURIER_PIN = "courier-1";
    private static final String ADDRESS = "1819 rue baile";
    private static final AuthenticatedAccount CUSTOMER =
            new AuthenticatedAccount(1, "Customer", "customer@example.com", "555-0100", Role.CUSTOMER, null);
    private static final AuthenticatedAccount GROCERIES =
            new AuthenticatedAccount(2, "Store", "store@example.com", "555-0101", Role.GROCERIES, null);
    private static final AuthenticatedAccount COURIER =
            new AuthenticatedAccount(3, "Courier", "courier@example.com", "555-0102", Role.COURIER, null);

    @TempDir
    Path tempDirectory;

    private Path database;
    private OrderStore orderStore;
    private LedgerService ledger;
    private OrderController controller;

    @BeforeEach
    void setUp() throws SQLException {
        database = tempDirectory.resolve("orders.sqlite");
        createDatabase(database);
        orderStore = new OrderStore(database);
        CityCatalog city = new CityCatalog(initialCity());
        PricingService pricing = new PricingService(new StandardPricingStrategy(),
                new PricingConfiguration(299, 0, 150, 1500, 250));
        ledger = new LedgerService(new LedgerStore(database));
        controller = new OrderController(city, new TestAccounts(), orderStore, pricing, ledger);
        controller.associateGroceriesAccount(GROCERIES.email(), STORE_ID);
    }

    @Test
    void placeOrderSavesPlacedWithOneTransition() throws SQLException {
        int id = placeOrder();

        assertSaved(id, "placed", List.of("placed"));
    }

    @Test
    void acceptPendingOrderSavesPreparingAndAddsTransition() throws SQLException {
        int id = placeOrder();

        controller.acceptPendingOrder(GROCERIES, id);

        assertSaved(id, "preparing", List.of("placed", "preparing"));
    }

    @Test
    void readyNextOrderSavesReadyAndOffersIdleCourier() throws SQLException {
        int id = placeOrder();
        controller.acceptPendingOrder(GROCERIES, id);

        controller.readyNextOrder(GROCERIES, id);

        assertSaved(id, "ready", List.of("placed", "preparing", "ready"));
        assertEquals(COURIER.id(), savedCourierId(id));
    }

    @Test
    void rejectPendingOrderSavesCancelledWithReasonAndRecordsCharge() throws SQLException {
        int id = placeOrder();

        controller.rejectPendingOrder(GROCERIES, id, "Out of stock");

        assertSaved(id, "canceled", List.of("placed", "canceled"));
        assertEquals("Out of stock", savedTransitions(id).get(1).getReason());
        assertTrue(ledger.hasChargeForOrder(id));
    }

    @Test
    void acceptCourierOfferSavesAcceptedWithoutTransition() throws SQLException {
        int id = readyOrder();

        controller.acceptCourierOffer(COURIER, id);

        // The database says "accepted" while the order object stays "ready": pinned as it is today.
        assertEquals("accepted", savedStatus(id));
        assertEquals("ready", liveOrder().getStatus());
        assertEquals(List.of("placed", "preparing", "ready"), savedStates(id));
    }

    @Test
    void pickupCourierOrderSavesPickedUpAndAddsTransition() throws SQLException {
        int id = readyOrder();
        controller.acceptCourierOffer(COURIER, id);

        controller.pickupCourierOrder(COURIER, id);

        assertSaved(id, "picked_up", List.of("placed", "preparing", "ready", "picked_up"));
    }

    @Test
    void deliverCourierOrderSavesDeliveredAndRecordsOneCharge() throws SQLException {
        int id = readyOrder();
        controller.acceptCourierOffer(COURIER, id);
        controller.pickupCourierOrder(COURIER, id);

        controller.deliverCourierOrder(COURIER, id);

        assertSaved(id, "delivered", List.of("placed", "preparing", "ready", "picked_up", "delivered"));
        assertTrue(ledger.hasChargeForOrder(id));
        assertEquals(1, ledger.entries().size());
    }

    @Test
    void refundOrderMarksRefundedWithoutChangingStatusOrSavedTransitions() throws SQLException {
        int id = readyOrder();
        controller.acceptCourierOffer(COURIER, id);
        controller.pickupCourierOrder(COURIER, id);
        controller.deliverCourierOrder(COURIER, id);

        controller.refundOrder(id);

        assertEquals("delivered", savedStatus(id));
        assertTrue(orderStore.loadAll().get(0).refunded());
        assertEquals(List.of("placed", "preparing", "ready", "picked_up", "delivered"), savedStates(id));
        assertEquals(List.of("placed", "preparing", "ready", "picked_up", "delivered", "refunded"),
                liveOrder().getTransitionEvents().stream().map(TransitionEvent::getToState).toList());
        assertEquals(2, ledger.entries().size());
    }

    @Test
    void resetClearsLedgerAndHistorySoReusedOrderIdsCanBeChargedAgain() throws SQLException {
        int first = deliveredOrder();

        controller.reset(initialCity());
        controller.associateGroceriesAccount(GROCERIES.email(), STORE_ID);
        int second = deliveredOrder();

        assertEquals(first, second);
        assertEquals(1, ledger.entries().size());
        assertEquals(List.of("placed", "preparing", "ready", "picked_up", "delivered"), savedStates(second));
    }

    private static CitySnapshot initialCity() {
        GroceryStore store = new GroceryStore(STORE_ID, "Test Store", 45.50, -73.57);
        store.addProduct(new Product("rice", "Rice", 7.50));
        return CitySnapshot.builder(new CityBounds(45.45, 45.56, -73.68, -73.40))
                .addGroceryStore(store)
                .addCourier(new CourierPin(COURIER_PIN, "Courier", 45.51, -73.56, CourierStatus.IDLE))
                .build();
    }

    private int deliveredOrder() throws SQLException {
        int id = readyOrder();
        controller.acceptCourierOffer(COURIER, id);
        controller.pickupCourierOrder(COURIER, id);
        controller.deliverCourierOrder(COURIER, id);
        return id;
    }

    private int placeOrder() throws SQLException {
        return controller.placeOrder(CUSTOMER, STORE_ID, ADDRESS, Map.of("rice", 2), false).getOrderId();
    }

    private int readyOrder() throws SQLException {
        int id = placeOrder();
        controller.acceptPendingOrder(GROCERIES, id);
        controller.readyNextOrder(GROCERIES, id);
        return id;
    }

    private Order liveOrder() throws SQLException {
        List<Order> orders = controller.customerOrders(CUSTOMER);
        assertEquals(1, orders.size());
        return orders.get(0);
    }

    private void assertSaved(int id, String status, List<String> states) throws SQLException {
        assertEquals(status, savedStatus(id));
        assertEquals(states, savedStates(id));
    }

    private String savedStatus(int id) throws SQLException {
        return saved(id).status();
    }

    private List<String> savedStates(int id) throws SQLException {
        return savedTransitions(id).stream().map(TransitionEvent::getToState).toList();
    }

    private List<TransitionEvent> savedTransitions(int id) throws SQLException {
        return saved(id).transitions();
    }

    private OrderStore.SavedOrder saved(int id) throws SQLException {
        return orderStore.loadAll().stream().filter(order -> order.id() == id).findFirst().orElseThrow();
    }

    private long savedCourierId(int id) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT courier_id FROM orders WHERE id = " + id)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
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