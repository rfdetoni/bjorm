package com.github.rfdetoni.bjorm;

/** SQL row-lock mode used only inside an active JDBC/Spring transaction. */
public enum LockMode {
    UPDATE("FOR UPDATE"),
    SHARE("FOR SHARE");

    private final String sql;
    LockMode(String sql) { this.sql = sql; }
    String sql() { return sql; }
}
