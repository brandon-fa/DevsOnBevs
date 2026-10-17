package com.littleisles.application.reg;

import com.littleisles.domain.reg.AccountCredentials;
import com.littleisles.technicalservices.reg.AccountRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class AuthControllerTest {
    @Test
    void returnsOneControllerInstance() throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("secret".getBytes(StandardCharsets.UTF_8)));
        AccountRepository repository = new AccountRepository() {
            @Override
            public AccountCredentials findByEmail(String email) {
                return new AccountCredentials(1, "customer", "Rae Chen", email, "555-0100", hash);
            }

            @Override
            public void saveSeededAccount(com.littleisles.domain.reg.Account account) {}
        };

        AuthController first = AuthController.getInstance(repository);
        AuthController second = AuthController.getInstance(repository);

        assertSame(first, second);
        AuthController.LoginResult result = first.login("rae@example.com", "secret");
        assertNotNull(result);
        assertEquals("customer", result.account().role().label());
        assertEquals(result.account(), first.currentAccount(result.sessionId()));
        assertNull(first.login("rae@example.com", "wrong"));
        first.logout(result.sessionId());
        assertNull(first.currentAccount(result.sessionId()));
    }
}
