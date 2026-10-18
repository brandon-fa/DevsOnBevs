package com.littleisles.domain.reg;

public enum Role {
    CUSTOMER("customer"),
    GROCERIES("groceries"),
    COURIER("courier"),
    OPERATOR("operator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Role fromLabel(String label) {
        if (label == null) {
            throw new IllegalArgumentException("Choose a role.");
        }
        for (Role role : values()) {
            if (role.label.equals(label)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Choose a role.");
    }
}
