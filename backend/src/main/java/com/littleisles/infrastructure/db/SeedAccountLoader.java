package com.littleisles.infrastructure.db;

import com.littleisles.application.reg.RegistrationService;
import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountDraft;
import com.littleisles.domain.reg.Role;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public final class SeedAccountLoader {
    private static final String PREFIX = "account.";

    private final SeedAccountRepository accountRepository;
    private final RegistrationService registrationService;
    private final Path configFile;

    public SeedAccountLoader(SeedAccountRepository accountRepository, RegistrationService registrationService, Path configFile) {
        this.accountRepository = accountRepository;
        this.registrationService = registrationService;
        this.configFile = configFile;
    }

    public List<SeededAccount> load() throws IOException, SQLException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        Set<String> accountIds = accountIds(properties);
        if (accountIds.isEmpty()) {
            throw new IllegalArgumentException("The seeded account configuration contains no accounts.");
        }

        List<Account> accounts = new ArrayList<>();
        Set<Role> roles = EnumSet.noneOf(Role.class);
        Set<String> emails = new HashSet<>();
        for (String accountId : accountIds) {
            String prefix = PREFIX + accountId + ".";
            String roleLabel = required(properties, prefix + "role");
            Role role = Role.fromLabel(roleLabel);
            AccountDraft draft = new AccountDraft(
                    required(properties, prefix + "fullName"),
                    required(properties, prefix + "email"),
                    required(properties, prefix + "password", false),
                    required(properties, prefix + "phone")
            );
            String normalizedEmail = draft.email().trim().toLowerCase(Locale.ROOT);
            if (!emails.add(normalizedEmail)) {
                throw new IllegalArgumentException("Seeded account emails must be unique: " + draft.email());
            }
            Account account = registrationService.register(role.label(), draft);
            if (account.getRole() != role) {
                throw new IllegalStateException("A seeded account must hold exactly one configured role.");
            }
            accounts.add(account);
            roles.add(role);
        }

        if (!roles.containsAll(EnumSet.allOf(Role.class))) {
            Set<Role> missingRoles = EnumSet.allOf(Role.class);
            missingRoles.removeAll(roles);
            throw new IllegalArgumentException("Seeded accounts are missing roles: " + missingRoles);
        }

        for (Account account : accounts) {
            accountRepository.saveSeededAccount(account);
        }
        return accounts.stream()
                .map(account -> new SeededAccount(account.getRole(), account.getEmail()))
                .toList();
    }

    public static Path locateConfigFile() {
        Path[] candidates = {
                Path.of("config", "seeded-accounts.properties"),
                Path.of("..", "config", "seeded-accounts.properties"),
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize();
        }
        throw new IllegalStateException("Could not find config/seeded-accounts.properties.");
    }

    private static Set<String> accountIds(Properties properties) {
        Set<String> ids = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(PREFIX)) {
                throw new IllegalArgumentException("Unexpected seeded account setting: " + key);
            }
            String remainder = key.substring(PREFIX.length());
            int separator = remainder.indexOf('.');
            if (separator <= 0 || separator == remainder.length() - 1) {
                throw new IllegalArgumentException("Invalid seeded account setting: " + key);
            }
            String field = remainder.substring(separator + 1);
            if (!Set.of("role", "fullName", "email", "password", "phone").contains(field)) {
                throw new IllegalArgumentException("Unknown seeded account field: " + field);
            }
            ids.add(remainder.substring(0, separator));
        }
        return ids;
    }

    private static String required(Properties properties, String key) {
        return required(properties, key, true);
    }

    private static String required(Properties properties, String key, boolean trim) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing seeded account setting: " + key);
        }
        return trim ? value.trim() : value;
    }

    public record SeededAccount(Role role, String email) {}
}
