package com.littleisles.domain.reg;

import com.littleisles.domain.ord.GroceryStore;
import java.util.Objects;

public final class GroceriesAccount extends Account {
    private GroceryStore store;

    public GroceriesAccount(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone, Role.GROCERIES);
    }

    public GroceryStore getStore() {
        return store;
    }

    public void setStore(GroceryStore store) {
        this.store = Objects.requireNonNull(store, "A groceries account needs a store.");
    }
}
