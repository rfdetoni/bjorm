package com.github.rfdetoni.bjorm;
import java.sql.Connection;

/** Explicit JDBC transaction isolation; never silently changes a Spring-managed transaction. */
public enum IsolationLevel {
    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

    private final int jdbc;
    IsolationLevel(int jdbc){this.jdbc=jdbc;}
    public int jdbc(){return jdbc;}
}
