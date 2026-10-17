package com.littleisles.domain.reg;

public record AccountCredentials(long id, String role, String fullName, String email, String phone, String passwordHash) {}
