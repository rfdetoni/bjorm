package com.github.rfdetoni.bjorm;

/** SQL row-lock mode used only inside an active JDBC/Spring transaction. */
public enum LockMode {
    UPDATE("FOR UPDATE"),
    SHARE("FOR SHARE"),
    /** Fail immediately instead of waiting for an existing exclusive row lock. */
    UPDATE_NOWAIT("FOR UPDATE NOWAIT"),
    /** Ignore locked rows; ideal for competing queue consumers. */
    UPDATE_SKIP_LOCKED("FOR UPDATE SKIP LOCKED"),
    /** Non-blocking shared lock request. */
    SHARE_NOWAIT("FOR SHARE NOWAIT"),
    /** Ignore rows that cannot be read with a shared lock immediately. */
    SHARE_SKIP_LOCKED("FOR SHARE SKIP LOCKED");

    private final String sql;
    LockMode(String sql) { this.sql = sql; }
    String sql() { return sql; }
}
