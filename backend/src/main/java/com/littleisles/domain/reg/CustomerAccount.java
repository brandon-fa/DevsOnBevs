package com.littleisles.domain.reg;

public final class CustomerAccount extends Account {
    public CustomerAccount(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone, Role.CUSTOMER);
    }
}
