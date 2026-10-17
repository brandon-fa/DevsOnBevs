package com.littleisles.domain.reg;

public final class RestaurantAccount extends Account {
    public RestaurantAccount(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone, Role.RESTAURANT);
    }
}
