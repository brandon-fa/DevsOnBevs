package com.littleisles.technicalservices.reg;

import com.littleisles.domain.ord.CourierRosterEntry;
import com.littleisles.domain.reg.Account;
import com.littleisles.domain.reg.AccountCredentials;
import java.sql.SQLException;
import java.util.List;

public interface AccountRepository {
    AccountCredentials findByEmail(String email) throws SQLException;
    void saveSeededAccount(Account account) throws SQLException;
    void updatePhoneAndPassword(long id, String phone, String passwordHash, String currentPasswordHash, String defaultAddress, boolean updateAddress) throws SQLException;
    void saveDefaultAddress(long id, String defaultAddress) throws SQLException;

    default Long courierUserId(String courierPin) throws SQLException {
        return null;
    }

    default String courierPinForUser(long userId) throws SQLException {
        return null;
    }

    default String registrationStoreCode(long userId) throws SQLException {
        return null;
    }

    default String linkedStoreId(long userId) throws SQLException {
        return null;
    }

    default String courierStatusForPin(String courierPin) throws SQLException {
        return null;
    }

    default void updateCourierStatus(long userId, String status) throws SQLException {
    }

    default List<CourierRosterEntry> listCouriers() throws SQLException {
        return List.of();
    }
}
