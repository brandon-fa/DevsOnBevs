package com.littleisles.domain.reg;

public final class CustomerCreator extends AccountCreator {
    @Override
    protected Account createAccount(AccountDraft draft, String passwordHash) {
        return new CustomerAccount(draft.fullName(), draft.email(), passwordHash, draft.phone());
    }

    @Override
    public Role supportedRole() {
        return Role.CUSTOMER;
    }
}
