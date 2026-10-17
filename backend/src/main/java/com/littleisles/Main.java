package com.littleisles;

import com.littleisles.application.reg.RegistrationService;
import com.littleisles.application.reg.AuthController;
import com.littleisles.infrastructure.db.AccountStore;
import com.littleisles.infrastructure.db.SeedAccountLoader;
import com.littleisles.infrastructure.http.RegistrationServer;
import com.sun.net.httpserver.HttpServer;

public final class Main {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        AccountStore accountStore = new AccountStore();
        SeedAccountLoader seedAccountLoader = new SeedAccountLoader(
                accountStore, RegistrationService.getInstance(), SeedAccountLoader.locateConfigFile());
        var seededAccounts = seedAccountLoader.load();
        AuthController authController = AuthController.getInstance(accountStore);
        HttpServer server = new RegistrationServer(RegistrationService.getInstance(), accountStore, authController).start(port);
        System.out.println("Seeded demonstration accounts available:");
        seededAccounts.forEach(account -> System.out.println("  " + account.role().label() + ": " + account.email()));
        System.out.println("Little Isles server listening on http://localhost:" + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }
}
