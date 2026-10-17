package com.littleisles.infrastructure.http;

import com.littleisles.application.reg.RegistrationService;
import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountDraft;
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

    public RegistrationServer(RegistrationService registrationService, AccountStore accountStore) {
        this.registrationService = registrationService;
        this.accountStore = accountStore;
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/accounts", this::handle);
        server.start();
        return server;
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
                + "\"role\":" + quote(account.getRole().label())
                + "}";
    }

    private static String errorJson(String message) {
        return "{\"error\":" + quote(message == null ? "The account could not be created." : message) + "}";
    }

    private static Map<String, String> readFields(String body) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String key : new String[] {"fullName", "email", "password", "phone", "role"}) {
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
