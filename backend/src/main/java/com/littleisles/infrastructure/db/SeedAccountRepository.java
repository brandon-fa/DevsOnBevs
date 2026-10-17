package com.littleisles.infrastructure.db;

import com.littleisles.domain.reg.Account;

import java.sql.SQLException;

public interface SeedAccountRepository extends AccountRepository {
    void saveSeededAccount(Account account) throws SQLException;
}
