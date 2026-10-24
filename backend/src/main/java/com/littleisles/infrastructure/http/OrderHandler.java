package com.littleisles.infrastructure.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.littleisles.application.dash.CommandDispatcher;
import com.littleisles.application.reg.AuthController;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.OrderLine;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrderHandler {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final AuthController authController;
    private final CommandDispatcher dispatcher;

    public OrderHandler(AuthController authController, CommandDispatcher dispatcher) {
        this.authController = authController;
        this.dispatcher = dispatcher;
    }

    public void bind(HttpServer server) {
        server.createContext("/api/orders", this::handleCustomerOrders);
        server.createContext("/api/store/orders", this::handleStoreOrders);
    }

    private void handleCustomerOrders(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestURI().getPath().equals("/api/orders")) {
            sendError(exchange, 404, "The order endpoint does not exist.");
            return;
        }

        String method = exchange.getRequestMethod();
        if (method.equals("OPTIONS")) {
            exchange.getResponseHeaders().set("Allow", "GET, POST, OPTIONS");
            send(exchange, 204, null);
            return;
        }
        if (!method.equals("GET") && !method.equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "GET, POST, OPTIONS");
            sendError(exchange, 405, "Use POST to place an order or GET to view your orders.");
            return;
        }

        AuthenticatedAccount account = authController.currentAccount(sessionCookie(exchange));
        if (account == null) {
            sendError(exchange, 401, "Log in to continue.");
            return;
        }

        try {
            if (method.equals("GET")) {
                JsonObject response = new JsonObject();
                response.add("orders", ordersJson(dispatcher.customerOrders(account)));
                send(exchange, 200, response);
            } else {
                JsonObject request = readBody(exchange);
                String storeId = requiredString(request, "storeId");
                String destination = requiredString(request, "destination");
                Map<String, Integer> products = readProducts(request);
                Order order = dispatcher.placeOrder(account, storeId, destination, products);
                send(exchange, 201, orderJson(order));
            }
        } catch (SecurityException exception) {
            sendError(exchange, 403, exception.getMessage());
        } catch (JsonParseException | ArithmeticException exception) {
            sendError(exchange, 400, "Send valid JSON with a storeId, destination, and product lines with whole-number quantities.");
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (IllegalStateException exception) {
            sendError(exchange, 409, exception.getMessage());
        } catch (SQLException exception) {
            sendError(exchange, 500, "The customer account could not be loaded. Please try again.");
        }
    }

    private void handleStoreOrders(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        boolean listing = path.equals("/api/store/orders");

        // An action URL contains an order id followed by accept, ready, or reject.
        if (!listing && !path.matches("/api/store/orders/[1-9][0-9]*/(accept|ready|reject)")) {
            sendError(exchange, 404, "The store order endpoint does not exist.");
            return;
        }

        String expectedMethod = "POST";
        if (listing) {
            expectedMethod = "GET";
        }
        String method = exchange.getRequestMethod();
        exchange.getResponseHeaders().set("Allow", expectedMethod + ", OPTIONS");
        if (method.equals("OPTIONS")) {
            send(exchange, 204, null);
            return;
        }
        if (!method.equals(expectedMethod)) {
            sendError(exchange, 405, "Use " + expectedMethod + " for this store order endpoint.");
            return;
        }

        AuthenticatedAccount account = authController.currentAccount(sessionCookie(exchange));
        if (account == null) {
            sendError(exchange, 401, "Log in to continue.");
            return;
        }

        try {
            GroceryStore store;
            if (listing) {
                store = dispatcher.storeOrders(account);
            } else {
                String[] parts = path.split("/");
                int orderId = Integer.parseInt(parts[4]);
                String action = parts[5];

                if (action.equals("accept")) {
                    store = dispatcher.acceptPendingOrder(account, orderId);
                } else if (action.equals("ready")) {
                    store = dispatcher.readyNextOrder(account, orderId);
                } else {
                    String reason = requiredString(readBody(exchange), "reason");
                    store = dispatcher.rejectPendingOrder(account, orderId, reason);
                }
            }
            send(exchange, 200, storeJson(store));
        } catch (SecurityException exception) {
            sendError(exchange, 403, exception.getMessage());
        } catch (NumberFormatException exception) {
            sendError(exchange, 400, "Choose a valid order id.");
        } catch (JsonParseException exception) {
            sendError(exchange, 400, "Send valid JSON containing a rejection reason.");
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (IllegalStateException exception) {
            sendError(exchange, 409, exception.getMessage());
        }
    }

    private static JsonObject readBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject request = JSON.fromJson(body, JsonObject.class);
        if (request == null) {
            throw new IllegalArgumentException("Send a JSON object.");
        }
        return request;
    }

    private static Map<String, Integer> readProducts(JsonObject request) {
        JsonElement lines = request.get("lines");
        if (lines == null || !lines.isJsonArray()) {
            throw new IllegalArgumentException("Provide an array of product lines.");
        }

        Map<String, Integer> products = new LinkedHashMap<>();
        for (JsonElement element : lines.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Each product line must be an object.");
            }
            JsonObject line = element.getAsJsonObject();
            String productId = requiredString(line, "productId");
            JsonElement quantity = line.get("quantity");
            if (quantity == null || !quantity.isJsonPrimitive() || !quantity.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Each product needs a whole-number quantity.");
            }
            int count = quantity.getAsBigDecimal().intValueExact();
            if (products.containsKey(productId)) {
                throw new IllegalArgumentException("Include each product only once with its total quantity.");
            }
            products.put(productId, count);
        }
        return products;
    }

    private static String requiredString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Provide a non-empty " + field + ".");
        }
        String text = value.getAsString().trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Provide a non-empty " + field + ".");
        }
        return text;
    }

    private static JsonArray ordersJson(List<Order> orders) {
        JsonArray result = new JsonArray();
        for (Order order : orders) {
            result.add(orderJson(order));
        }
        return result;
    }

    private static JsonObject storeJson(GroceryStore store) {
        JsonObject result = new JsonObject();
        result.addProperty("storeId", store.getId());
        result.addProperty("storeName", store.getName());
        result.add("pending", ordersJson(store.getPendingOrders()));
        result.add("preparing", ordersJson(store.getPreparationQueue().getOrders()));
        result.add("ready", ordersJson(store.getReadyOrders()));
        return result;
    }

    private static JsonObject orderJson(Order order) {
        JsonObject result = new JsonObject();
        result.addProperty("orderId", order.getOrderId());
        result.addProperty("storeId", order.getStore().getId());
        result.addProperty("storeName", order.getStore().getName());
        result.addProperty("status", order.getStatus());
        result.addProperty("destination", order.getDestination());
        if (order.getRejectionReason() != null) {
            result.addProperty("rejectionReason", order.getRejectionReason());
        }

        JsonArray lines = new JsonArray();
        long subtotalCents = 0;
        for (OrderLine line : order.getOrderLines()) {
            long priceCents = Math.round(line.getProduct().getPrice() * 100);
            JsonObject product = new JsonObject();
            product.addProperty("productId", line.getProduct().getId());
            product.addProperty("name", line.getProduct().getName());
            product.addProperty("quantity", line.getQuantity());
            product.addProperty("priceCents", priceCents);
            lines.add(product);
            subtotalCents += priceCents * line.getQuantity();
        }
        result.add("lines", lines);
        result.addProperty("subtotalCents", subtotalCents);
        result.add("transitionEvents", JSON.toJsonTree(order.getTransitionEvents()));
        return result;
    }

    private static String sessionCookie(HttpExchange exchange) {
        for (String cookie : exchange.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String part : cookie.split(";")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && pair[0].equals("littleisle.session")) {
                    return pair[1];
                }
            }
        }
        return null;
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        send(exchange, status, error);
    }

    private static void send(HttpExchange exchange, int status, JsonElement body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        byte[] bytes = JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
