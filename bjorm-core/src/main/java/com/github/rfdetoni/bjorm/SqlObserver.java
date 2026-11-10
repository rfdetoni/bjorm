package com.github.rfdetoni.bjorm;

/**
 * Optional, security-first JDBC execution telemetry.
 *
 * Never receives the SQL string, bound parameter values, exception messages or
 * tenant schema identifier. Duration covers the JDBC execute call, not complete
 * ResultSet consumption. Callbacks run on the application thread and should be fast.
 */
@FunctionalInterface
public interface SqlObserver {
    void completed(SqlOperation operation,long durationNanos,boolean succeeded);
}
