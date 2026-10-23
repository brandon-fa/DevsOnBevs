package com.littleisles.infrastructure.http;

import com.littleisles.application.ord.CityCatalog;
import com.littleisles.application.reg.AuthController;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class CityCatalogHandler {
    private final AuthController authController;
    private final CityCatalog cityCatalog;

    public CityCatalogHandler(AuthController authController, CityCatalog cityCatalog) {
        this.authController = authController;
        this.cityCatalog = cityCatalog;
    }

    public void bind(HttpServer server) {
        server.createContext("/api/city", this::handle);
    }

    private void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Use GET to load the city.\"}");
            return;
        }
        AuthenticatedAccount account = authController.currentAccount(sessionCookie(exchange));
        if (account == null) {
            send(exchange, 401, "{\"error\":\"Log in to continue.\"}");
            return;
        }
        send(exchange, 200, cityCatalog.snapshot().toJson());
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
