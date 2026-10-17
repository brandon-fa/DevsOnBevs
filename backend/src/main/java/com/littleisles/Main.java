package com.littleisles;

import com.littleisles.application.reg.RegistrationService;
import com.littleisles.infrastructure.db.AccountStore;
import com.littleisles.infrastructure.http.RegistrationServer;
import com.sun.net.httpserver.HttpServer;

public final class Main {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = new RegistrationServer(new RegistrationService(), new AccountStore()).start(port);
        System.out.println("Registration server listening on http://localhost:" + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }
}
