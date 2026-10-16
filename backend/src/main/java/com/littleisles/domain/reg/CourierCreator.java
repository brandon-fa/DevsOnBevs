package com.littleisles.domain.reg;

public final class CourierCreator extends AccountCreator {
    @Override
    protected Account createAccount(AccountDraft draft, String passwordHash) {
        return new CourierAccount(draft.fullName(), draft.email(), passwordHash, draft.phone());
    }

    @Override
    public Role supportedRole() {
        return Role.COURIER;
    }
}
