package com.littleisles.infrastructure.http;

import com.littleisles.application.ord.CityCatalog;
import com.littleisles.application.reg.RegistrationService;
import com.littleisles.application.reg.AuthController;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountDraft;
import com.littleisles.domain.reg.CustomerAccount;
import com.littleisles.domain.reg.GroceriesAccount;
import com.littleisles.infrastructure.db.AccountStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RegistrationServer {
    private final RegistrationService registrationService;
    private final AccountStore accountStore;
    private final AuthController authController;
    private final CityCatalog cityCatalog;

    public RegistrationServer(RegistrationService registrationService, AccountStore accountStore, AuthController authController, CityCatalog cityCatalog) {
        this.registrationService = registrationService;
        this.accountStore = accountStore;
        this.authController = authController;
        this.cityCatalog = cityCatalog;
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/accounts", this::handle);
        server.createContext("/api/login", this::handleLogin);
        server.createContext("/api/session", this::handleSession);
        server.createContext("/api/logout", this::handleLogout);
        server.createContext("/api/account", this::handleAccount);
        server.start();
        return server;
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { send(exchange, 204, ""); return; }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Use POST to log in.\"}"); return;
        }
        Map<String, String> fields = readFields(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        try {
            AuthController.LoginResult result = authController.login(fields.get("email"), fields.get("password"));
            if (result == null) {
                send(exchange, 401, "{\"error\":\"Email or password is incorrect.\"}"); return;
            }
            exchange.getResponseHeaders().add("Set-Cookie", "littleisle.session=" + result.sessionId() + "; Path=/; HttpOnly; SameSite=Lax");
            send(exchange, 200, accountJson(result.account()));
        } catch (SQLException exception) {
            send(exchange, 500, "{\"error\":\"Login is temporarily unavailable.\"}");
        }
    }

    private void handleSession(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) { send(exchange, 405, "{}"); return; }
        AuthenticatedAccount account = authController.currentAccount(sessionCookie(exchange));
        if (account == null) { send(exchange, 401, "{\"error\":\"Log in to continue.\"}"); return; }
        send(exchange, 200, accountJson(account));
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { send(exchange, 405, "{}"); return; }
        authController.logout(sessionCookie(exchange));
        exchange.getResponseHeaders().add("Set-Cookie", "littleisle.session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        send(exchange, 204, "");
    }

    private void handleAccount(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { send(exchange, 204, ""); return; }
        if (!"PATCH".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Use PATCH to update the account.\"}"); return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            AuthenticatedAccount account = authController.updateProfile(
                    sessionCookie(exchange),
                    stringField(body, "phone"),
                    stringField(body, "currentPassword"),
                    stringField(body, "newPassword"),
                    stringField(body, "defaultAddress"));
            if (account == null) { send(exchange, 401, "{\"error\":\"Log in to continue.\"}"); return; }
            send(exchange, 200, accountJson(account));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, errorJson(exception.getMessage()));
        } catch (SQLException exception) {
            send(exchange, 500, "{\"error\":\"The account could not be saved.\"}");
        }
    }

    private static String sessionCookie(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        for (String part : cookie.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals("littleisle.session")) return pair[1];
        }
        return null;
    }

    private String accountJson(AuthenticatedAccount account) {
        String storeCode = "";
        String storeAddress = "";
        if ("groceries".equals(account.role().label()) && cityCatalog != null) {
            try {
                String code = accountStore.registrationStoreCode(account.id());
                String storeId = accountStore.linkedStoreId(account.id());
                if (code != null) storeCode = code;
                if (storeId != null) {
                    for (GroceryStore store : cityCatalog.snapshot().groceryStores()) {
                        if (store.getId().equals(storeId) && store.getLocation() != null) {
                            storeAddress = store.getLocation();
                        }
                    }
                }
            } catch (SQLException exception) {
                storeCode = "";
                storeAddress = "";
            }
        }
        return "{\"id\":" + account.id() + ",\"fullName\":" + quote(account.fullName())
                + ",\"email\":" + quote(account.email()) + ",\"phone\":" + quote(account.phone())
                + ",\"role\":" + quote(account.role().label())
                + ",\"defaultAddress\":" + quote(account.defaultAddress() == null ? "" : account.defaultAddress())
                + ",\"storeCode\":" + quote(storeCode)
                + ",\"storeAddress\":" + quote(storeAddress) + "}";
    }

    private void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Use POST to create an account.\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> fields = readFields(body);
        try {
            Account account = registrationService.register(
                    fields.get("role"),
                    new AccountDraft(
                            fields.get("fullName"),
                            fields.get("email"),
                            fields.get("password"),
                            fields.get("phone")
                    )
            );
            if (account instanceof CustomerAccount customer) {
                customer.setDefaultAddress(fields.get("defaultAddress"));
            }
            if (account instanceof GroceriesAccount groceries) {
                String storeCode = fields.get("storeCode");
                if (storeCode == null || storeCode.isBlank()) {
                    throw new IllegalArgumentException("Enter the store code.");
                }
                if (!accountStore.storeCodeExists(storeCode)) {
                    throw new IllegalArgumentException("That store code does not match an existing grocery store.");
                }
                groceries.setStoreCode(storeCode.trim());
            }
            long id = accountStore.insert(account);
            send(exchange, 201, accountJson(id, account));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            send(exchange, 400, errorJson(exception.getMessage()));
        } catch (SQLException exception) {
            if (AccountStore.isDuplicateEmail(exception)) {
                send(exchange, 409, "{\"error\":\"An account with that email already exists.\"}");
                return;
            }
            send(exchange, 500, "{\"error\":\"The account could not be saved.\"}");
        }
    }

    private static String accountJson(long id, Account account) {
        return "{"
                + "\"id\":" + id + ","
                + "\"fullName\":" + quote(account.getFullName()) + ","
                + "\"email\":" + quote(account.getEmail()) + ","
                + "\"phone\":" + quote(account.getPhone()) + ","
                + "\"role\":" + quote(account.getRole().label()) + ","
                + "\"defaultAddress\":" + quote(account instanceof CustomerAccount customer && customer.getDefaultAddress() != null ? customer.getDefaultAddress() : "")
                + "}";
    }

    private static String errorJson(String message) {
        return "{\"error\":" + quote(message == null ? "The account could not be created." : message) + "}";
    }

    private static Map<String, String> readFields(String body) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String key : new String[] {"fullName", "email", "password", "phone", "role", "storeCode", "defaultAddress"}) {
            fields.put(key, stringField(body, key));
        }
        return fields;
    }

    private static String stringField(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyAt = json.indexOf(needle);
        if (keyAt < 0) {
            return "";
        }
        int colon = json.indexOf(':', keyAt + needle.length());
        if (colon < 0) {
            return "";
        }
        int open = json.indexOf('"', colon + 1);
        if (open < 0) {
            return "";
        }
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int index = open + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (escaping) {
                value.append(switch (current) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> current;
                });
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                break;
            }
            value.append(current);
        }
        return value.toString();
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                + "\"";
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
