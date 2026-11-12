package com.littleisles.infrastructure.http;

import com.google.gson.JsonObject;
import com.littleisles.application.dash.CommandDispatcher;
import com.littleisles.application.reg.AuthController;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** POST /api/stores/{storeId}/pause or /reopen: change a grocery store's status (R-DASH-01). */
public final class StoreStatusHandler {
    private static final Pattern PATH = Pattern.compile("/api/stores/([a-z0-9-]+)/(pause|reopen)");

    private final AuthController authController;
    private final CommandDispatcher dispatcher;

    public StoreStatusHandler(AuthController authController, CommandDispatcher dispatcher) {
        this.authController = authController;
        this.dispatcher = dispatcher;
    }

    public void bind(HttpServer server) {
        server.createContext("/api/stores", this::handle);
    }

    private void handle(HttpExchange exchange) throws IOException {
        Matcher path = PATH.matcher(exchange.getRequestURI().getPath());
        if (!path.matches()) {
            send(exchange, 404, error("The store endpoint does not exist."));
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, error("Use POST to pause or reopen a grocery store."));
            return;
        }
        AuthenticatedAccount account = authController.currentAccount(sessionCookie(exchange));
        if (account == null) {
            send(exchange, 401, error("Log in to continue."));
            return;
        }

        String storeId = path.group(1);
        String action = path.group(2);
        GroceryStore store;
        try {
            if (action.equals("pause")) {
                store = dispatcher.pauseStore(account, storeId);
            } else {
                store = dispatcher.reopenStore(account, storeId);
            }
        } catch (SecurityException exception) {
            send(exchange, 403, error(exception.getMessage()));
            return;
        } catch (IllegalStateException exception) {
            send(exchange, 409, error(exception.getMessage()));
            return;
        } catch (IllegalArgumentException exception) {
            send(exchange, 404, error(exception.getMessage()));
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("storeId", store.getId());
        body.addProperty("status", store.getStatus().label());
        body.addProperty("message", store.getName() + (action.equals("pause") ? " is paused." : " is open again."));
        send(exchange, 200, body.toString());
    }

    private static String error(String message) {
        JsonObject body = new JsonObject();
        body.addProperty("error", message);
        return body.toString();
    }

    private static String sessionCookie(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) {
            return null;
        }
        for (String part : cookie.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals("littleisle.session")) {
                return pair[1];
            }
        }
        return null;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}