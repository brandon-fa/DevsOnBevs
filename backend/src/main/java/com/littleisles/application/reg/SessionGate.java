package com.littleisles.application.reg;

import com.littleisles.domain.reg.AuthenticatedAccount;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionGate {
    private SessionGate() {}

    private static class InstanceHolder {
        private static final SessionGate INSTANCE = new SessionGate();
    }

    public static SessionGate getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private final ConcurrentHashMap<String, AuthenticatedAccount> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String establish(AuthenticatedAccount account) {
        byte[] token = new byte[32];
        random.nextBytes(token);
        String sessionId = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        sessions.put(sessionId, account);
        return sessionId;
    }

    public AuthenticatedAccount find(String sessionId) {
        return sessionId == null ? null : sessions.get(sessionId);
    }

    public void invalidate(String sessionId) {
        if (sessionId != null) sessions.remove(sessionId);
    }
}
