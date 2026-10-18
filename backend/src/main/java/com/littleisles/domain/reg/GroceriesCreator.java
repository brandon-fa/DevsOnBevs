package com.littleisles.domain.reg;

public final class GroceriesCreator extends AccountCreator {
    @Override
    protected Account createAccount(AccountDraft draft, String passwordHash) {
        return new GroceriesAccount(draft.fullName(), draft.email(), passwordHash, draft.phone());
    }

    @Override
    public Role supportedRole() {
        return Role.GROCERIES;
    }
}
