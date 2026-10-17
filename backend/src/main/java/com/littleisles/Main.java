package com.littleisles;

import com.littleisles.application.reg.RegistrationService;
import com.littleisles.application.reg.AuthController;
import com.littleisles.infrastructure.db.AccountStore;
import com.littleisles.infrastructure.http.RegistrationServer;
import com.sun.net.httpserver.HttpServer;

public final class Main {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        AccountStore accountStore = new AccountStore();
        AuthController authController = AuthController.getInstance(accountStore);
        HttpServer server = new RegistrationServer(RegistrationService.getInstance(), accountStore, authController).start(port);
        System.out.println("Little Isles server listening on http://localhost:" + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }
}
