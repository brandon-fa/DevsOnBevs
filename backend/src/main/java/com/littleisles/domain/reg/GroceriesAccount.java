package com.littleisles.domain.reg;

import com.littleisles.domain.ord.GroceryStore;
import java.util.Objects;

public final class GroceriesAccount extends Account {
    private GroceryStore store;
    private String storeCode;

    public GroceriesAccount(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone, Role.GROCERIES);
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        if (storeCode == null || storeCode.isBlank()) {
            throw new IllegalArgumentException("Enter the store code.");
        }
        this.storeCode = storeCode.trim();
    }

    public GroceryStore getStore() {
        return store;
    }

    public void setStore(GroceryStore store) {
        this.store = Objects.requireNonNull(store, "A groceries account needs a store.");
    }
}
