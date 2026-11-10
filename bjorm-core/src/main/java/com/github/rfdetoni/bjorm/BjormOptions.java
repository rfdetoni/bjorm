package com.github.rfdetoni.bjorm;

/** Resource budgets are scoped to a BJORM instance, never stored globally. */
public record BjormOptions(int queryTimeoutSeconds,int maxBufferedRows,int fetchSize,int batchSize) {
    /** Source-compatible constructor retained for existing integrations. */
    public BjormOptions(int queryTimeoutSeconds,int maxBufferedRows,int fetchSize) {
        this(queryTimeoutSeconds,maxBufferedRows,fetchSize,256);
    }
    public BjormOptions {
        if(queryTimeoutSeconds<0 || maxBufferedRows<1 || fetchSize<1 || batchSize<1 || batchSize>10000)
            throw new IllegalArgumentException("Invalid JDBC timeout, buffer, fetch or batch size");
    }
    /** Suitable starting limits for server applications; tune to their SLA. */
    public static BjormOptions defaults() { return new BjormOptions(30,100_000,128,256); }
}
