package com.littleisles.technicalservices.reg;

import com.littleisles.application.reg.RegistrationService;
import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountCredentials;
import com.littleisles.domain.reg.AccountService;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.Role;
import com.littleisles.infrastructure.db.AccountStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SeedAccountLoaderTest {
    @TempDir
    Path tempDirectory;

    @Test
    void loadsOneLoginReadyAccountForEveryRoleAndIsIdempotent() throws Exception {
        Path config = writeConfig(allRolesConfig());
        InMemorySeedAccountRepository repository = new InMemorySeedAccountRepository();
        SeedAccountLoader loader = new SeedAccountLoader(repository, RegistrationService.getInstance(), config);

        List<SeedAccountLoader.SeededAccount> loaded = loader.load();
        Set<Role> loadedRoles = loaded.stream().map(SeedAccountLoader.SeededAccount::role).collect(Collectors.toSet());
        assertEquals(EnumSet.allOf(Role.class), loadedRoles);
        assertEquals(4, repository.accounts.size());

        AccountService accountService = new AccountService();
        for (Role role : Role.values()) {
            AccountCredentials account = repository.findByEmail("demo." + role.label() + "@example.com");
            AuthenticatedAccount authenticated = accountService.authenticate(account, "DemoPass123!");
            assertNotNull(authenticated);
            assertEquals(role, authenticated.role());
        }

        loader.load();
        assertEquals(4, repository.accounts.size());
    }

    @Test
    void rejectsConfigurationMissingARoleBeforeSavingAnyAccounts() throws Exception {
        Path config = writeConfig(allRolesConfig().replace(accountBlock("operator", "operator"), ""));
        InMemorySeedAccountRepository repository = new InMemorySeedAccountRepository();
        SeedAccountLoader loader = new SeedAccountLoader(repository, RegistrationService.getInstance(), config);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, loader::load);

        assertTrue(error.getMessage().contains("OPERATOR"));
        assertTrue(repository.accounts.isEmpty());
    }

    @Test
    void persistsSeededAccountsInSqliteAndKeepsOneCopyOnRepeatedLoads() throws Exception {
        Path config = writeConfig(allRolesConfig());
        Path database = tempDirectory.resolve("seeded.sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, role TEXT NOT NULL, full_name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, phone TEXT NOT NULL)");
            statement.execute("CREATE TABLE customer_profiles (user_id INTEGER PRIMARY KEY, delivery_address TEXT NOT NULL)");
            statement.execute("CREATE TABLE groceries_profiles (user_id INTEGER PRIMARY KEY, store_name TEXT NOT NULL, cuisine TEXT NOT NULL, address TEXT NOT NULL)");
            statement.execute("CREATE TABLE courier_profiles (user_id INTEGER PRIMARY KEY, vehicle_type TEXT NOT NULL)");
        }
        AccountStore store = new AccountStore(database);
        SeedAccountLoader loader = new SeedAccountLoader(store, RegistrationService.getInstance(), config);

        loader.load();
        loader.load();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM users")) {
            assertTrue(rows.next());
            assertEquals(4, rows.getInt(1));
        }
        AccountCredentials customer = store.findByEmail("demo.customer@example.com");
        assertNotNull(new AccountService().authenticate(customer, "DemoPass123!"));
    }

    private Path writeConfig(String contents) throws IOException {
        Path config = tempDirectory.resolve("seeded-accounts.properties");
        Files.writeString(config, contents);
        return config;
    }

    private static String allRolesConfig() {
        return accountBlock("customer", "customer")
                + accountBlock("groceries", "groceries")
                + accountBlock("courier", "courier")
                + accountBlock("operator", "operator");
    }

    private static String accountBlock(String id, String role) {
        return "account." + id + ".role=" + role + "\n"
                + "account." + id + ".fullName=Demo " + role + "\n"
                + "account." + id + ".email=demo." + role + "@example.com\n"
                + "account." + id + ".password=DemoPass123!\n"
                + "account." + id + ".phone=514-555-0200\n\n";
    }

    private static final class InMemorySeedAccountRepository implements AccountRepository {
        private final Map<String, AccountCredentials> accounts = new HashMap<>();

        @Override
        public AccountCredentials findByEmail(String email) {
            return accounts.get(email.toLowerCase(Locale.ROOT));
        }

        @Override
        public void saveSeededAccount(Account account) {
            String key = account.getEmail().toLowerCase(Locale.ROOT);
            accounts.put(key, new AccountCredentials(0, account.getRole().label(), account.getFullName(),
                    account.getEmail(), account.getPhone(), account.getPasswordHash()));
        }
    }
}
