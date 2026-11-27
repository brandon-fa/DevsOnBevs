package com.littleisles.infrastructure.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.littleisles.application.dash.CommandDispatcher;
import com.littleisles.application.ntf.NotificationCenter;
import com.littleisles.application.ord.CityCatalog;
import com.littleisles.application.ord.OrderController;
import com.littleisles.application.prc.LedgerService;
import com.littleisles.application.prc.PricingService;
import com.littleisles.application.reg.AuthController;
import com.littleisles.application.reg.SessionGate;
import com.littleisles.domain.ord.CityBounds;
import com.littleisles.domain.ord.CitySnapshot;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.ord.Product;
import com.littleisles.domain.prc.PricingConfiguration;
import com.littleisles.domain.prc.StandardPricingStrategy;
import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountCredentials;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.Role;
import com.littleisles.infrastructure.db.LedgerStore;
import com.littleisles.infrastructure.db.OrderStore;
import com.littleisles.technicalservices.reg.AccountRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class OrderHandlerTest {
    private static final String ORDERS = "/api/orders";
    private static final String STORE_ORDERS = "/api/store/orders";
    private static final String ORDER_BODY = """
            {"storeId":"store-1","destination":"1819 rue baile","lines":[{"productId":"rice","quantity":2}]}
            """;
    private static final String INVALID_ORDER_JSON =
            "Send valid JSON with a storeId, destination, and product lines with whole-number quantities.";

    @TempDir
    Path tempDirectory;

    private final SessionGate sessions = SessionGate.getInstance();
    private HttpServer server;
    private HttpClient client;
    private String customerSession;
    private String storeSession;

    @BeforeEach
    void startServer() throws Exception {
        Path database = tempDirectory.resolve("orders.sqlite");
        createDatabase(database);
        AccountRepository accounts = new TestAccounts();
        GroceryStore store = new GroceryStore("store-1", "Test Store", 45.50, -73.57);
        store.addProduct(new Product("rice", "Rice", 7.50));
        CityCatalog city = new CityCatalog(CitySnapshot.builder(new CityBounds(45.45, 45.56, -73.68, -73.40))
                .addGroceryStore(store).build());
        PricingService pricing = new PricingService(new StandardPricingStrategy(),
                new PricingConfiguration(299, 0, 150, 1500, 250));
        OrderController controller = new OrderController(city, accounts, new OrderStore(database),
                pricing, new LedgerService(new LedgerStore(database)));
        controller.associateGroceriesAccount("store@example.com", "store-1");

        // The public factory retains its first repository across test classes.
        // Create a separate controller without reading or changing the singleton instance.
        var constructor = AuthController.class.getDeclaredConstructor(AccountRepository.class, SessionGate.class);
        constructor.setAccessible(true);
        AuthController auth = constructor.newInstance(accounts, sessions);
        customerSession = sessions.establish(new AuthenticatedAccount(
                1, "Customer", "customer@example.com", "555-0100", Role.CUSTOMER, null));
        storeSession = sessions.establish(new AuthenticatedAccount(
                2, "Store", "store@example.com", "555-0101", Role.GROCERIES, null));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        new OrderHandler(auth, new CommandDispatcher(controller, null, new NotificationCenter(Duration.ofSeconds(60)))).bind(server);
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        sessions.invalidate(customerSession);
        sessions.invalidate(storeSession);
    }

    @Test
    void customerOptionsReturnsAllowAndEmptyBody() throws Exception {
        HttpResponse<String> response = send("OPTIONS", ORDERS, null, null);

        assertResponse(response, 204, "GET, POST, OPTIONS");
        assertEquals("", response.body());
    }

    @Test
    void customerPutRejectsMethodBeforeAuthentication() throws Exception {
        assertError(send("PUT", ORDERS, null, null), 405, "GET, POST, OPTIONS",
                "Use POST to place an order or GET to view your orders.");
    }

    @Test
    void customerGetWithoutSessionRequiresLogin() throws Exception {
        assertError(send("GET", ORDERS, null, null), 401, null, "Log in to continue.");
    }

    @Test
    void customerRouteRejectsExtraPath() throws Exception {
        assertError(send("GET", ORDERS + "/extra", null, null), 404, null,
                "The order endpoint does not exist.");
    }

    @Test
    void customerPostRejectsMalformedJson() throws Exception {
        assertError(send("POST", ORDERS, customerSession, "{"), 400, null, INVALID_ORDER_JSON);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            "2"         | Each product needs a whole-number quantity.
            1.5         | Send valid JSON with a storeId, destination, and product lines with whole-number quantities.
            2147483648  | Send valid JSON with a storeId, destination, and product lines with whole-number quantities.
            0           | Each product needs an id and a positive whole-number quantity.
            -1          | Each product needs an id and a positive whole-number quantity.
            """)
    void customerPostRejectsInvalidQuantities(String quantity, String message) throws Exception {
        String body = ORDER_BODY.replace("\"quantity\":2", "\"quantity\":" + quantity);

        assertError(send("POST", ORDERS, customerSession, body), 400, null, message);
    }

    @Test
    void customerPostRejectsDuplicateProductIds() throws Exception {
        String body = ORDER_BODY.replace("}]", "},{\"productId\":\"rice\",\"quantity\":1}]");

        assertError(send("POST", ORDERS, customerSession, body), 400, null,
                "Include each product only once with its total quantity.");
    }

    @Test
    void customerGetRejectsGroceriesRole() throws Exception {
        assertError(send("GET", ORDERS, storeSession, null), 403, null,
                "Only customers can place or view their orders.");
    }

    @Test
    void customerPostRejectsGroceriesRoleAfterParsing() throws Exception {
        assertError(send("POST", ORDERS, storeSession, ORDER_BODY), 403, null,
                "Only customers can place or view their orders.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/0/accept", "/01/ready", "/abc/reject", "/1/cancel", "/1/reject/extra"})
    void storeOrderRejectsInvalidActionRoutes(String suffix) throws Exception {
        assertError(send("POST", STORE_ORDERS + suffix, null, null), 404, null,
                "The store order endpoint does not exist.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "ready", "reject"})
    void storeActionsAdvertisePostAndRequireLoginAndGroceriesRole(String action) throws Exception {
        String path = STORE_ORDERS + "/1/" + action;
        HttpResponse<String> options = send("OPTIONS", path, null, null);
        assertResponse(options, 204, "POST, OPTIONS");
        assertEquals("", options.body());

        assertError(send("GET", path, null, null), 405, "POST, OPTIONS",
                "Use POST for this store order endpoint.");
        assertError(send("POST", path, null, null), 401, "POST, OPTIONS", "Log in to continue.");
        assertError(send("POST", path, customerSession, "{\"reason\":\"Unavailable\"}"),
                403, "POST, OPTIONS", "Only groceries accounts can manage store orders.");
    }

    @Test
    void storeActionRejectsAnOrderIdOutsideIntegerRange() throws Exception {
        assertError(send("POST", STORE_ORDERS + "/2147483648/accept", storeSession, null),
                400, "POST, OPTIONS", "Choose a valid order id.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":\"   \"}"})
    void storeRejectRequiresNonEmptyReason(String body) throws Exception {
        assertError(send("POST", STORE_ORDERS + "/1/reject", storeSession, body), 400, "POST, OPTIONS",
                "Provide a non-empty reason.");
    }

    @Test
    void storeRejectReportsMalformedJson() throws Exception {
        assertError(send("POST", STORE_ORDERS + "/1/reject", storeSession, "{"), 400, "POST, OPTIONS",
                "Send valid JSON containing a rejection reason.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            accept | This order is no longer pending at your store.
            ready  | Only the next order in the preparation queue can be marked ready.
            reject | This order is no longer pending at your store.
            """)
    void storeActionsReportOrderStateConflicts(String action, String message) throws Exception {
        assertError(send("POST", STORE_ORDERS + "/1/" + action, storeSession, "{\"reason\":\"Unavailable\"}"),
                409, "POST, OPTIONS", message);
    }

    @Test
    void customerPlacesOrderAndStoreMovesItFromPendingToReady() throws Exception {
        JsonObject placed = placeOrder();
        int id = placed.get("orderId").getAsInt();
        assertTrue(id > 0);
        assertEquals("store-1", placed.get("storeId").getAsString());
        assertEquals("1819 rue baile", placed.get("destination").getAsString());
        assertEquals("placed", placed.get("status").getAsString());
        assertEquals(1949, placed.get("totalCents").getAsInt());
        assertEquals(JsonParser.parseString("""
                [{"productId":"rice","name":"Rice","quantity":2,"priceCents":750}]
                """), placed.getAsJsonArray("lines"));

        JsonObject pending = responseJson(send("GET", STORE_ORDERS, storeSession, null), 200, "GET, OPTIONS");
        assertOrderInQueue(pending, "pending", id, "placed");

        JsonObject accepted = responseJson(send("POST", STORE_ORDERS + "/" + id + "/accept", storeSession, null),
                200, "POST, OPTIONS");
        assertTrue(accepted.getAsJsonArray("pending").isEmpty());
        assertOrderInQueue(accepted, "preparing", id, "preparing");

        JsonObject ready = responseJson(send("POST", STORE_ORDERS + "/" + id + "/ready", storeSession, null),
                200, "POST, OPTIONS");
        assertTrue(ready.getAsJsonArray("preparing").isEmpty());
        assertOrderInQueue(ready, "ready", id, "ready");

        JsonObject customerOrders = responseJson(send("GET", ORDERS, customerSession, null), 200, null);
        assertOrderInQueue(customerOrders, "orders", id, "ready");
    }

    @Test
    void storeRejectRemovesPendingOrderAndCustomerSeesReason() throws Exception {
        int id = placeOrder().get("orderId").getAsInt();

        JsonObject rejected = responseJson(send("POST", STORE_ORDERS + "/" + id + "/reject", storeSession,
                "{\"reason\":\"  Out of stock  \"}"), 200, "POST, OPTIONS");

        assertEquals("store-1", rejected.get("storeId").getAsString());
        assertTrue(rejected.getAsJsonArray("pending").isEmpty());
        JsonObject customerOrders = responseJson(send("GET", ORDERS, customerSession, null), 200, null);
        assertOrderInQueue(customerOrders, "orders", id, "canceled");
        assertEquals("Out of stock", customerOrders.getAsJsonArray("orders").get(0)
                .getAsJsonObject().get("rejectionReason").getAsString());
    }

    private JsonObject placeOrder() throws Exception {
        return responseJson(send("POST", ORDERS, customerSession, ORDER_BODY), 201, null);
    }

    private HttpResponse<String> send(String method, String path, String session, String body) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5));
        if (session != null) {
            request.header("Cookie", "littleisle.session=" + session);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertResponse(HttpResponse<String> response, int status, String allow) {
        assertEquals(status, response.statusCode(), response::body);
        assertEquals(allow, response.headers().firstValue("Allow").orElse(null));
        assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElse(null));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
    }

    private static JsonObject responseJson(HttpResponse<String> response, int status, String allow) {
        assertResponse(response, status, allow);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static void assertError(HttpResponse<String> response, int status, String allow, String message) {
        JsonObject expected = new JsonObject();
        expected.addProperty("error", message);
        assertEquals(expected, responseJson(response, status, allow));
    }

    private static void assertOrderInQueue(JsonObject response, String queue, int id, String status) {
        var orders = response.getAsJsonArray(queue);
        assertEquals(1, orders.size());
        JsonObject order = orders.get(0).getAsJsonObject();
        assertEquals(id, order.get("orderId").getAsInt());
        assertEquals(status, order.get("status").getAsString());
    }

    private static void createDatabase(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
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
    }
}
