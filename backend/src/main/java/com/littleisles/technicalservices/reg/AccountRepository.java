package com.littleisles.technicalservices.reg;

import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountCredentials;
import java.sql.SQLException;

public interface AccountRepository {
    AccountCredentials findByEmail(String email) throws SQLException;
    void saveSeededAccount(Account account) throws SQLException;
}
