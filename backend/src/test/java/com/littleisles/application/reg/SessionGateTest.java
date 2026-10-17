package com.littleisles.application.reg;

import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionGateTest {
    @Test
    void establishesFindsAndInvalidatesSession() {
        SessionGate gate = SessionGate.getInstance();
        assertSame(gate, SessionGate.getInstance());
        AuthenticatedAccount account = new AuthenticatedAccount(7, "Rae Chen", "rae@example.com", "555-0100", Role.OPERATOR);

        String sessionId = gate.establish(account);

        assertNotNull(sessionId);
        assertEquals(account, gate.find(sessionId));
        gate.invalidate(sessionId);
        assertNull(gate.find(sessionId));
    }
}
