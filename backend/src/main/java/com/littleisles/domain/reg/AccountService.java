package com.littleisles.domain.reg;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AccountService {
    public AuthenticatedAccount authenticate(AccountCredentials account, String password) {
        if (account == null || password == null || password.isEmpty()) return null;
        if (!MessageDigest.isEqual(hash(password).getBytes(StandardCharsets.US_ASCII),
                account.passwordHash().getBytes(StandardCharsets.US_ASCII))) return null;
        Role role;
        try {
            role = Role.fromLabel(account.role());
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (!PermissionPolicy.hasDashboardAccess(role)) return null;
        return new AuthenticatedAccount(account.id(), account.fullName(), account.email(), account.phone(), role);
    }

    private static String hash(String password) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
