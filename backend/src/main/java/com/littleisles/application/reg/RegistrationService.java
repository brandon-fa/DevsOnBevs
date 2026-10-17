package com.littleisles.application.reg;

import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountDraft;
import com.littleisles.domain.reg.AccountCreator;
import com.littleisles.domain.reg.CourierCreator;
import com.littleisles.domain.reg.CustomerCreator;
import com.littleisles.domain.reg.OperatorCreator;
import com.littleisles.domain.reg.RestaurantCreator;
import com.littleisles.domain.reg.Role;

public final class RegistrationService {
    public Account register(String roleLabel, AccountDraft draft) {
        Role role = Role.fromLabel(roleLabel == null ? "" : roleLabel.trim());
        Account account = creatorFor(role).register(draft);
        if (account.getRole() != role) {
            throw new IllegalStateException("An account must hold exactly one role.");
        }
        return account;
    }

    private static AccountCreator creatorFor(Role role) {
        return switch (role) {
            case CUSTOMER -> new CustomerCreator();
            case RESTAURANT -> new RestaurantCreator();
            case COURIER -> new CourierCreator();
            case OPERATOR -> new OperatorCreator();
        };
    }
}
