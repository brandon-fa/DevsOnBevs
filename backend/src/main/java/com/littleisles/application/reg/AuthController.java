package com.littleisles.application.reg;

import com.littleisles.domain.reg.AccountService;
import com.littleisles.domain.reg.AuthenticatedAccount;
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
        return sessionGate.find(sessionId);
    }

    public void logout(String sessionId) {
        sessionGate.invalidate(sessionId);
    }

    public record LoginResult(String sessionId, AuthenticatedAccount account) {}
}
