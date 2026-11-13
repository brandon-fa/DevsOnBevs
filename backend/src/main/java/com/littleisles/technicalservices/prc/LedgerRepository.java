package com.littleisles.technicalservices.prc;

import com.littleisles.domain.prc.LedgerEntry;

import java.sql.SQLException;
import java.util.List;

public interface LedgerRepository {
    void save(LedgerEntry entry) throws SQLException;

    List<LedgerEntry> findAll() throws SQLException;
}
