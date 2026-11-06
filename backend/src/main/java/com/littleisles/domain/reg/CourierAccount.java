package com.littleisles.domain.reg;

public final class CourierAccount extends Account {
    private String courierPin;

    public CourierAccount(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone, Role.COURIER);
    }

    public String getCourierPin() {
        return courierPin;
    }

    public void setCourierPin(String courierPin) {
        if (courierPin == null || courierPin.isBlank()) {
            throw new IllegalArgumentException("A courier needs a map id.");
        }
        this.courierPin = courierPin.trim();
    }
}
