package com.littleisles.domain.reg;

public final class RestaurantCreator extends AccountCreator {
    @Override
    protected Account createAccount(AccountDraft draft, String passwordHash) {
        return new RestaurantAccount(draft.fullName(), draft.email(), passwordHash, draft.phone());
    }

    @Override
    public Role supportedRole() {
        return Role.RESTAURANT;
    }
}
