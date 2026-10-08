package com.github.rfdetoni.bjorm;

/** Resource budgets are scoped to a BJORM instance, never stored globally. */
public record BjormOptions(int queryTimeoutSeconds,int maxBufferedRows,int fetchSize) {
    public BjormOptions {
        if(queryTimeoutSeconds<0 || maxBufferedRows<1 || fetchSize<1)
            throw new IllegalArgumentException("Invalid JDBC query timeout, materialization limit or fetch size");
    }
    /** Suitable starting limits for server applications; tune to their SLA. */
    public static BjormOptions defaults() { return new BjormOptions(30,100_000,128); }
}
