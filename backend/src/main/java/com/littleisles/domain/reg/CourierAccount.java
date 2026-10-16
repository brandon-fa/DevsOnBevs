package com.littleisles.domain.reg;

public final class CourierAccount extends Account {
    public CourierAccount(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone, Role.COURIER);
    }
}
