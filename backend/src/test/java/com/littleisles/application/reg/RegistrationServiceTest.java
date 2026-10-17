package com.littleisles.application.reg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class RegistrationServiceTest {
    @Test
    void returnsTheSharedRegistrationService() {
        assertSame(RegistrationService.getInstance(), RegistrationService.getInstance());
    }
}
