package com.github.rfdetoni.bjorm;

import java.sql.Connection;

/**
 * Optional isolation for a BJORM-owned transaction.
 * Levels are applied before the transaction begins, then restored.
 */
public enum TransactionIsolation {
    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

    private final int jdbcLevel;

    TransactionIsolation(int jdbcLevel) {
        this.jdbcLevel = jdbcLevel;
    }

    int jdbcLevel() {
        return jdbcLevel;
    }
}
