package com.littleisles.application.reg;

import com.littleisles.domain.reg.AccountService;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.Role;
import com.littleisles.technicalservices.reg.AccountRepository;

import java.sql.SQLException;

public final class AuthController {
    private static volatile AuthController instance;

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final SessionGate sessionGate;

    private AuthController(AccountRepository accountRepository, SessionGate sessionGate) {
        this.accountRepository = accountRepository;
        this.accountService = new AccountService();
        this.sessionGate = sessionGate;
    }

    public static AuthController getInstance(AccountRepository accountRepository) {
        if (instance == null) {
            synchronized (AuthController.class) {
                if (instance == null) instance = new AuthController(accountRepository, SessionGate.getInstance());
            }
        }
        return instance;
    }

    public LoginResult login(String email, String password) throws SQLException {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) return null;
        AuthenticatedAccount account = accountService.authenticate(
                accountRepository.findByEmail(email.trim()), password);
        return account == null ? null : new LoginResult(sessionGate.establish(account), account);
    }

    public AuthenticatedAccount currentAccount(String sessionId) {
        AuthenticatedAccount current = sessionGate.find(sessionId);
        if (current == null) return null;
        try {
            var stored = accountRepository.findByEmail(current.email());
            if (stored == null) return current;
            AuthenticatedAccount fresh = new AuthenticatedAccount(
                    current.id(), current.fullName(), current.email(), stored.phone(), current.role(),
                    stored.defaultAddress());
            sessionGate.replace(sessionId, fresh);
            return fresh;
        } catch (SQLException exception) {
            return current;
        }
    }

    public void logout(String sessionId) {
        sessionGate.invalidate(sessionId);
    }

    public AuthenticatedAccount updateProfile(String sessionId, String phone, String currentPassword, String newPassword, String defaultAddress) throws SQLException {
        AuthenticatedAccount current = sessionGate.find(sessionId);
        if (current == null) return null;
        String cleanedPhone = phone == null ? "" : phone.trim();
        if (cleanedPhone.isEmpty()) throw new IllegalArgumentException("Enter a phone number.");
        boolean updateAddress = current.role() == Role.CUSTOMER;
        String cleanedAddress = null;
        if (updateAddress && defaultAddress != null && !defaultAddress.isBlank()) {
            cleanedAddress = defaultAddress.trim();
        }
        String nextHash = null;
        String currentHash = null;
        if (newPassword != null && !newPassword.isEmpty()) {
            if (currentPassword == null || currentPassword.isEmpty()) {
                throw new IllegalArgumentException("Enter your current password to set a new one.");
            }
            currentHash = accountService.hashPassword(currentPassword);
            if (accountService.authenticate(accountRepository.findByEmail(current.email()), currentPassword) == null) {
                throw new IllegalArgumentException("Current password is incorrect.");
            }
            nextHash = accountService.hashPassword(newPassword);
        }
        accountRepository.updatePhoneAndPassword(current.id(), cleanedPhone, nextHash, currentHash, cleanedAddress, updateAddress);
        AuthenticatedAccount updated = new AuthenticatedAccount(
                current.id(), current.fullName(), current.email(), cleanedPhone, current.role(),
                updateAddress ? cleanedAddress : current.defaultAddress());
        sessionGate.replace(sessionId, updated);
        return updated;
    }

    public record LoginResult(String sessionId, AuthenticatedAccount account) {}
}
