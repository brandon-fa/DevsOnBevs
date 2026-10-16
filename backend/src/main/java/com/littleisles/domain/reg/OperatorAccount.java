package com.littleisles.domain.reg;

public final class OperatorAccount extends Account {
    public OperatorAccount(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone, Role.OPERATOR);
    }
}
