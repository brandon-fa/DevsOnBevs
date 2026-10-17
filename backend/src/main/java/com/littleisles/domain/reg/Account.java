package com.littleisles.domain.reg;

public abstract class Account {
    private final String fullName;
    private final String email;
    private final String passwordHash;
    private final String phone;
    private final Role role;

    protected Account(String fullName, String email, String passwordHash, String phone, Role role) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Enter your full name.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Enter a password.");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Enter a phone number.");
        }
        if (role == null) {
            throw new IllegalArgumentException("Choose a role.");
        }
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public final Role getRole() {
        return role;
    }
}
