package com.littleisles.domain.reg;

public record AuthenticatedAccount(long id, String fullName, String email, String phone, Role role) {}
