package com.littleisles.application.ord;

import com.littleisles.application.prc.LedgerService;
import com.littleisles.application.prc.PricingService;
import com.littleisles.domain.ord.CityBounds;
import com.littleisles.domain.ord.CitySnapshot;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.OrderLine;
import com.littleisles.domain.ord.Product;
import com.littleisles.domain.ord.StoreStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class OrderControllerTest {
    private static final String STORE_ID = "store-1";
    private static final String ADDRESS = "1819 rue baile";
    private static final AuthenticatedAccount CUSTOMER =
            new AuthenticatedAccount(1, "Customer", "customer@example.com", "555-0100", Role.CUSTOMER, null);
    private static final AuthenticatedAccount GROCERIES =
            new AuthenticatedAccount(2, "Store", "store@example.com", "555-0101", Role.GROCERIES, null);

    @TempDir
    Path tempDirectory;

    private Path database;
    private OrderStore orderStore;
    private LedgerService ledger;
    private OrderController controller;
    private GroceryStore store;
    private final TestAccounts accounts = new TestAccounts();

    @BeforeEach
    void setUp() throws SQLException {
        database = tempDirectory.resolve("orders.sqlite");
        createDatabase(database);
        orderStore = new OrderStore(database);
        restartController();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidOrders")
    void rejectsInvalidPlacementWithoutCreatingAnOrder(String message, String storeId, String destination,
                                                       Map<String, Integer> products) throws SQLException {
        var error = assertThrowsExactly(IllegalArgumentException.class,
                () -> controller.placeOrder(CUSTOMER, storeId, destination, products, true));

        assertEquals(message, error.getMessage());
        assertNoOrders();
    }

    private static Stream<Arguments> invalidOrders() {
        return Stream.of(
                Arguments.of("Choose a grocery store.", " ", "", Map.of()),
                Arguments.of("Enter a delivery address.", STORE_ID, " ", Map.of()),
                Arguments.of("Add at least one product to your order.", STORE_ID, ADDRESS, Map.of()),
                Arguments.of("The grocery store does not exist: missing", "missing", ADDRESS, Map.of("rice", 1)),
                Arguments.of("Each product needs an id and a positive whole-number quantity.",
                        STORE_ID, ADDRESS, Map.of("rice", 0)),
                Arguments.of("Each product needs an id and a positive whole-number quantity.",
                        STORE_ID, ADDRESS, Map.of(" ", 1)),
                Arguments.of("A product does not belong to this grocery store.",
                        STORE_ID, ADDRESS, Map.of("unknown", 1)));
    }

    @Test
    void checksCustomerRoleBeforeValidatingOrderInput() throws SQLException {
        var error = assertThrowsExactly(SecurityException.class,
                () -> controller.placeOrder(GROCERIES, null, null, null, true));

        assertEquals("Only customers can place or view their orders.", error.getMessage());
        assertNoOrders();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            PAUSED | This grocery store is paused and is not taking new orders.
            CLOSED | This grocery store is closed.
            """)
    void rejectsUnavailableStores(StoreStatus status, String message) throws SQLException {
        store.setStatus(status);
        var error = assertThrowsExactly(IllegalStateException.class, () -> placeRiceOrder());

        assertEquals(message, error.getMessage());
        assertNoOrders();
    }

    @Test
    void rejectsSoldOutProductWithoutKeepingEarlierValidLines() throws SQLException {
        store.getProducts().stream().filter(product -> product.getId().equals("tea"))
                .findFirst().orElseThrow().setSoldOut(true);
        Map<String, Integer> products = new LinkedHashMap<>();
        products.put("rice", 1);
        products.put("tea", 1);
        var error = assertThrowsExactly(IllegalStateException.class,
                () -> controller.placeOrder(CUSTOMER, STORE_ID, ADDRESS, products, true));

        assertEquals("Tea is sold out.", error.getMessage());
        assertNoOrders();
    }

    @Test
    void placesAndPersistsPricedOrderInInputLineOrder() throws SQLException {
        Map<String, Integer> products = new LinkedHashMap<>();
        products.put("tea", 1);
        products.put("rice", 2);

        Order order = controller.placeOrder(CUSTOMER, STORE_ID, "  " + ADDRESS + "  ", products, true);

        assertTrue(order.getOrderId() > 0);
        assertEquals("placed", order.getStatus());
        assertEquals(ADDRESS, order.getDestination());
        assertEquals(ADDRESS, accounts.savedAddress);
        assertEquals(List.of(order), store.getPendingOrders());
        assertEquals(List.of(order), controller.customerOrders(CUSTOMER));
        assertEquals(List.of("Tea", "Rice"), productNames(order.getOrderLines()));
        assertEquals(1750, order.getFare().subtotalCents());
        assertEquals(List.of("placed"), transitionStates(order));

        var saved = orderStore.loadAll();
        assertEquals(1, saved.size());
        assertEquals(order.getOrderId(), saved.get(0).id());
        assertEquals(CUSTOMER.id(), saved.get(0).customerId());
        assertEquals(ADDRESS, saved.get(0).destination());
        assertEquals("placed", saved.get(0).status());
        assertEquals(List.of("Tea", "Rice"), productNames(saved.get(0).lines()));
        assertEquals(List.of(1, 2), saved.get(0).lines().stream().map(OrderLine::getQuantity).toList());
        assertEquals(order.getFare(), saved.get(0).fare());
        assertEquals(transitions(order.getTransitionEvents()), transitions(saved.get(0).transitions()));
    }

    @Test
    void rollsBackDatabaseRowsAndPendingOrderWhenLineInsertFails() throws SQLException {
        executeSql("""
                CREATE TRIGGER reject_order_line BEFORE INSERT ON order_items
                BEGIN SELECT RAISE(ABORT, 'fixture rejects order lines'); END
                """);

        var error = assertThrows(SQLException.class,
                () -> controller.placeOrder(CUSTOMER, STORE_ID, ADDRESS, Map.of("rice", 2), true));

        assertEquals("[SQLITE_CONSTRAINT_TRIGGER] A RAISE function within a trigger fired, causing the SQL statement to abort (fixture rejects order lines)",
                error.getMessage());
        assertNoOrders();
        assertEquals(0, countRows("orders"));
        assertEquals(0, countRows("order_items"));
        assertEquals(0, countRows("products"));
        assertEquals(0, countRows("order_transition_events"));
    }

    @Test
    void defaultAddressFailureRemovesPendingOrderButLeavesPersistedOrder() throws SQLException {
        accounts.addressFailure = new SQLException("fixture cannot save default address");

        assertSame(accounts.addressFailure, assertThrowsExactly(SQLException.class,
                () -> controller.placeOrder(CUSTOMER, STORE_ID, ADDRESS, Map.of("rice", 2), true)));

        assertTrue(store.getPendingOrders().isEmpty());
        assertTrue(controller.customerOrders(CUSTOMER).isEmpty());
        assertEquals(1, countRows("orders"));
        assertEquals(1, countRows("order_items"));
        restartController();
        assertEquals(List.of("placed"), controller.customerOrders(CUSTOMER).stream().map(Order::getStatus).toList());
        assertEquals(controller.customerOrders(CUSTOMER), store.getPendingOrders());
    }

    @Test
    void restoresPlacedPreparingReadyAndCanceledOrdersInPersistedIdOrder() throws SQLException {
        Order placedFirst = placeRiceOrder();
        Order preparingFirst = placeRiceOrder();
        Order readyFirst = placeRiceOrder();
        Order canceled = placeRiceOrder();
        Order readySecond = placeRiceOrder();
        Order preparingSecond = placeRiceOrder();
        Order placedSecond = placeRiceOrder();

        for (Order order : List.of(readySecond, readyFirst)) {
            controller.acceptPendingOrder(GROCERIES, order.getOrderId());
            controller.readyNextOrder(GROCERIES, order.getOrderId());
        }
        controller.acceptPendingOrder(GROCERIES, preparingSecond.getOrderId());
        controller.acceptPendingOrder(GROCERIES, preparingFirst.getOrderId());
        controller.rejectPendingOrder(GROCERIES, canceled.getOrderId(), "Out of stock");
        List<Order> originalOrders = controller.customerOrders(CUSTOMER);

        restartController();

        List<Order> restored = controller.customerOrders(CUSTOMER);
        assertEquals(ids(originalOrders), ids(restored));
        assertEquals(List.of("placed", "preparing", "ready", "canceled", "ready", "preparing", "placed"),
                restored.stream().map(Order::getStatus).toList());
        assertEquals(ids(List.of(placedFirst, placedSecond)), ids(store.getPendingOrders()));
        assertEquals(ids(List.of(preparingFirst, preparingSecond)), ids(store.getPreparationQueue().getOrders()));
        assertEquals(ids(List.of(readyFirst, readySecond)), ids(store.getReadyOrders()));
        for (int index = 0; index < restored.size(); index++) {
            Order order = restored.get(index);
            Order original = originalOrders.get(index);
            assertNotSame(original, order);
            assertSame(store, order.getStore());
            assertEquals(CUSTOMER.id(), order.getCustomerUserId());
            assertEquals(original.getFare(), order.getFare());
            assertEquals(transitions(original.getTransitionEvents()), transitions(order.getTransitionEvents()));
            assertEquals(List.of("Rice"), productNames(order.getOrderLines()));
        }
        assertEquals("Out of stock", restored.get(3).getTransitionEvents().get(1).getReason());
        assertTrue(ledger.hasChargeForOrder(canceled.getOrderId()));
        assertEquals(1, ledger.entries().size());
    }

    @Test
    void restoresAndPersistsRepricedLegacyFare() throws SQLException {
        Order placed = placeRiceOrder();
        executeSql("""
                UPDATE orders
                SET subtotal_cents = 0, delivery_fee_cents = 0, service_fee_cents = 0,
                    small_order_surcharge_cents = 0, total_cents = 0, fare_version = 0
                """);

        restartController();

        assertEquals(placed.getFare(), store.getPendingOrders().get(0).getFare());
        var saved = orderStore.loadAll().get(0);
        assertEquals(1, saved.fareVersion());
        assertEquals(placed.getFare(), saved.fare());
    }

    @Test
    void backfillsMissingCanceledOrderChargeOnce() throws SQLException {
        Order placed = placeRiceOrder();
        controller.rejectPendingOrder(GROCERIES, placed.getOrderId(), "Out of stock");
        executeSql("DELETE FROM ledger_entry_timestamps");
        executeSql("DELETE FROM ledger_entries");

        restartController();
        assertTrue(ledger.hasChargeForOrder(placed.getOrderId()));
        var backfilledEntries = ledger.entries();
        assertEquals(1, backfilledEntries.size());

        restartController();
        assertEquals(backfilledEntries, ledger.entries());
    }

    @Test
    void skipsSavedOrdersForStoresMissingFromCityCatalog() throws SQLException {
        Order placed = placeRiceOrder();
        executeSql("UPDATE grocery_stores SET id = 'missing-from-city'");

        restartController();

        assertTrue(controller.customerOrders(CUSTOMER).isEmpty());
        assertTrue(store.getPendingOrders().isEmpty());
        var saved = orderStore.loadAll();
        assertEquals(1, saved.size());
        assertEquals(placed.getOrderId(), saved.get(0).id());
        assertEquals("missing-from-city", saved.get(0).storeId());
    }

    private void restartController() throws SQLException {
        store = new GroceryStore(STORE_ID, "Test Store", 45.50, -73.57);
        store.addProduct(new Product("rice", "Rice", 7.50));
        store.addProduct(new Product("tea", "Tea", 2.50));
        CityCatalog city = new CityCatalog(CitySnapshot.builder(new CityBounds(45.45, 45.56, -73.68, -73.40))
                .addGroceryStore(store).build());
        PricingService pricing = new PricingService(new StandardPricingStrategy(),
                new PricingConfiguration(299, 0, 150, 1500, 250));
        ledger = new LedgerService(new LedgerStore(database));
        controller = new OrderController(city, accounts, orderStore, pricing, ledger);
        controller.associateGroceriesAccount(GROCERIES.email(), STORE_ID);
    }

    private Order placeRiceOrder() throws SQLException {
        return controller.placeOrder(CUSTOMER, STORE_ID, ADDRESS, Map.of("rice", 2), false);
    }

    private void assertNoOrders() throws SQLException {
        assertTrue(store.getPendingOrders().isEmpty());
        assertTrue(controller.customerOrders(CUSTOMER).isEmpty());
        assertTrue(orderStore.loadAll().isEmpty());
        assertNull(accounts.savedAddress);
    }

    private static List<Integer> ids(List<Order> orders) {
        return orders.stream().map(Order::getOrderId).toList();
    }

    private static List<String> productNames(List<OrderLine> lines) {
        return lines.stream().map(line -> line.getProduct().getName()).toList();
    }

    private static List<String> transitionStates(Order order) {
        return order.getTransitionEvents().stream().map(TransitionEvent::getToState).toList();
    }

    private static List<SavedTransition> transitions(List<TransitionEvent> events) {
        return events.stream().map(event -> new SavedTransition(
                event.getFromState(), event.getToState(), event.getTimestamp(), event.getReason())).toList();
    }

    private record SavedTransition(String from, String to, String timestamp, String reason) {}

    private int countRows(String table) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private void executeSql(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute(sql);
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
            statement.execute("INSERT INTO users VALUES (1, 'customer', NULL), (2, 'groceries', 'STORE-CODE')");
            statement.execute("INSERT INTO grocery_stores VALUES ('store-1', 'store-code')");
        }
    }

    private static final class TestAccounts implements AccountRepository {
        private String savedAddress;
        private SQLException addressFailure;

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
        public void saveDefaultAddress(long id, String defaultAddress) throws SQLException {
            if (addressFailure != null) {
                throw addressFailure;
            }
            assertEquals(CUSTOMER.id(), id);
            savedAddress = defaultAddress;
        }
    }
}
