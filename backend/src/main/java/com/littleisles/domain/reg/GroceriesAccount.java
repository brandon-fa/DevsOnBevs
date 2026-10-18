package com.littleisles.domain.reg;

public final class GroceriesAccount extends Account {
    public GroceriesAccount(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone, Role.GROCERIES);
    }
}
