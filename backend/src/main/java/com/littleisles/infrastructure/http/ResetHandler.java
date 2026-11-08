package com.littleisles.infrastructure.http;

import com.google.gson.JsonObject;
import com.littleisles.application.dash.CommandDispatcher;
import com.littleisles.application.reg.AuthController;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/** Reset: POST /api/reset restores the initial scenario (operator only). */
public final class ResetHandler {
    private final AuthController authController;
    private final CommandDispatcher dispatcher;

    public ResetHandler(AuthController authController, CommandDispatcher dispatcher) {
        this.authController = authController;
        this.dispatcher = dispatcher;
    }

    public void bind(HttpServer server) {
        server.createContext("/api/reset", this::handle);
    }

    private void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, json("error", "Use POST to reset the simulator."));
            return;
        }
        AuthenticatedAccount account = authController.currentAccount(sessionCookie(exchange));
        if (account == null) {
            send(exchange, 401, json("error", "Log in to continue."));
            return;
        }
        try {
            dispatcher.reset(account);
        } catch (SecurityException exception) {
            send(exchange, 403, json("error", exception.getMessage()));
            return;
        } catch (IOException | SQLException exception) {
            send(exchange, 500, json("error", "The simulator could not be reset. Please try again."));
            return;
        }
        send(exchange, 200, json("message", "The simulator was reset to its initial scenario."));
    }

    private static String json(String key, String text) {
        JsonObject body = new JsonObject();
        body.addProperty(key, text);
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
