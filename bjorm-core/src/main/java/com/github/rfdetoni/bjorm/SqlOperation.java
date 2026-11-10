package com.github.rfdetoni.bjorm;

/** Coarse statement category. No SQL text, bound parameter or tenant identifier is exposed. */
public enum SqlOperation {
    QUERY,
    UPDATE,
    BATCH
}
