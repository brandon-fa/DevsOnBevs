package com.littleisles.domain.reg;

public final class OperatorAccount extends Account {
    private String storeCode;

    public OperatorAccount(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone, Role.OPERATOR);
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }
}
