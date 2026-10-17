package com.littleisles.infrastructure.db;

import com.littleisles.domain.reg.AccountCredentials;
import java.sql.SQLException;

public interface AccountRepository {
    AccountCredentials findByEmail(String email) throws SQLException;
}
