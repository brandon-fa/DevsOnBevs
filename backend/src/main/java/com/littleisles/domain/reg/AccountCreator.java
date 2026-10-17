package com.littleisles.domain.reg;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public abstract class AccountCreator {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public final Account register(AccountDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Account details are required.");
        }

        String fullName = normalize(draft.fullName());
        String email = normalize(draft.email());
        String phone = normalize(draft.phone());
        String password = draft.password() == null ? "" : draft.password();

        if (fullName.isEmpty()) {
            throw new IllegalArgumentException("Enter your full name.");
        }
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (password.isEmpty()) {
            throw new IllegalArgumentException("Enter a password.");
        }
        if (phone.isEmpty()) {
            throw new IllegalArgumentException("Enter a phone number.");
        }

        AccountDraft cleaned = new AccountDraft(fullName, email, password, phone);
        Account account = createAccount(cleaned, hashPassword(password));
        if (account == null || account.getRole() != supportedRole()) {
            throw new IllegalStateException("An account must hold exactly one role.");
        }
        return account;
    }

    protected abstract Account createAccount(AccountDraft draft, String passwordHash);

    public abstract Role supportedRole();

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String hashPassword(String password) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
