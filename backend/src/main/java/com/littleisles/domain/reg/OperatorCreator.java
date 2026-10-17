package com.littleisles.domain.reg;

public final class OperatorCreator extends AccountCreator {
    @Override
    protected Account createAccount(AccountDraft draft, String passwordHash) {
        return new OperatorAccount(draft.fullName(), draft.email(), passwordHash, draft.phone());
    }

    @Override
    public Role supportedRole() {
        return Role.OPERATOR;
    }
}
