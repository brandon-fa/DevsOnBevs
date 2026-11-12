package com.littleisles;

import com.littleisles.application.ord.CityCatalog;
import com.littleisles.application.ord.OrderController;
import com.littleisles.application.dash.CommandDispatcher;
import com.littleisles.application.reg.RegistrationService;
import com.littleisles.application.reg.AuthController;
import com.littleisles.domain.ord.CitySnapshot;
import com.littleisles.domain.reg.Role;
import com.littleisles.infrastructure.db.AccountStore;
import com.littleisles.infrastructure.http.CityCatalogHandler;
import com.littleisles.infrastructure.http.RegistrationServer;
import com.littleisles.infrastructure.http.OrderHandler;
import com.littleisles.infrastructure.http.ResetHandler;
import com.littleisles.infrastructure.http.StoreStatusHandler;
import com.littleisles.technicalservices.ord.CityConfigLoader;
import com.littleisles.technicalservices.reg.SeedAccountLoader;
import com.sun.net.httpserver.HttpServer;
import java.util.List;

public final class Main {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        RegistrationService registrationService = RegistrationService.getInstance();
        AccountStore accountStore = new AccountStore();
        SeedAccountLoader seedAccountLoader = new SeedAccountLoader(
                accountStore, registrationService, SeedAccountLoader.locateConfigFile());
        List<SeedAccountLoader.SeededAccount> seededAccounts = seedAccountLoader.load();
        AuthController authController = AuthController.getInstance(accountStore);
        CityConfigLoader cityLoader = new CityConfigLoader(CityConfigLoader.locateConfigFile());
        CitySnapshot city = cityLoader.load();
        CityCatalog cityCatalog = new CityCatalog(city);
        cityCatalog.overlayStoredCouriers(accountStore.couriersWithLocation());
        OrderController orderController = new OrderController(cityCatalog, accountStore);
        for (SeedAccountLoader.SeededAccount account : seededAccounts) {
            if (account.role() == Role.GROCERIES) {
                orderController.associateGroceriesAccount(account.email(), account.storeId());
            }
        }
        RegistrationServer registrationServer = new RegistrationServer(registrationService, accountStore, authController, cityCatalog);
        HttpServer server = registrationServer.start(port);
        CommandDispatcher dispatcher = new CommandDispatcher(orderController, cityLoader);
        new CityCatalogHandler(authController, dispatcher).bind(server);
        new OrderHandler(authController, dispatcher).bind(server);
        new ResetHandler(authController, dispatcher).bind(server);
        new StoreStatusHandler(authController, dispatcher).bind(server);
        System.out.println("Seeded demonstration accounts available:");
        for (SeedAccountLoader.SeededAccount account : seededAccounts) {
            System.out.println("  " + account.role().label() + ": " + account.email());
        }
        System.out.println("Little Isles server listening on http://localhost:" + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }
}
