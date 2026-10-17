package com.littleisles.domain.reg;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {
    private final AccountService accountService = new AccountService();

    @Test
    void authenticatesMatchingPasswordAndIdentifiesRole() throws Exception {
        AccountCredentials credentials = account("courier", "correct horse");

        AuthenticatedAccount authenticated = accountService.authenticate(credentials, "correct horse");

        assertNotNull(authenticated);
        assertEquals(Role.COURIER, authenticated.role());
        assertEquals("Rae Chen", authenticated.fullName());
    }

    @Test
    void rejectsWrongPasswordAndMissingAccount() throws Exception {
        AccountCredentials credentials = account("customer", "secret");

        assertNull(accountService.authenticate(credentials, "wrong"));
        assertNull(accountService.authenticate(null, "secret"));
    }

    private static AccountCredentials account(String role, String password) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(password.getBytes(StandardCharsets.UTF_8)));
        return new AccountCredentials(42, role, "Rae Chen", "rae@example.com", "555-0100", hash);
    }
}
