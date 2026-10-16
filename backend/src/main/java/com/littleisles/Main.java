package com.littleisles;

import com.littleisles.application.reg.RegistrationService;
import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountDraft;

public final class Main {
    public static void main(String[] args) {
        String role = args.length > 0 ? args[0] : "customer";
        String fullName = args.length > 1 ? args[1] : "Amira Benali";
        String email = args.length > 2 ? args[2] : "amira@example.com";
        String password = args.length > 3 ? args[3] : "secret-pass";
        String phone = args.length > 4 ? args[4] : "514-555-0199";

        Account account = new RegistrationService().register(
                role,
                new AccountDraft(fullName, email, password, phone)
        );

        System.out.println("Account created");
        System.out.println("Role: " + account.getRole().label());
        System.out.println("Type: " + account.getClass().getSimpleName());
        System.out.println("Name: " + account.getFullName());
        System.out.println("Email: " + account.getEmail());
        System.out.println("Phone: " + account.getPhone());
    }
}
