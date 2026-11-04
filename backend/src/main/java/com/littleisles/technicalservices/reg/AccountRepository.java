package com.littleisles.technicalservices.reg;

import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountCredentials;
import java.sql.SQLException;

public interface AccountRepository {
    AccountCredentials findByEmail(String email) throws SQLException;
    void saveSeededAccount(Account account) throws SQLException;
    void updatePhoneAndPassword(long id, String phone, String passwordHash, String currentPasswordHash, String defaultAddress, boolean updateAddress) throws SQLException;
    void saveDefaultAddress(long id, String defaultAddress) throws SQLException;
}
