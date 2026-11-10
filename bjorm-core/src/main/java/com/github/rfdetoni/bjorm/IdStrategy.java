package com.github.rfdetoni.bjorm;

/**
 * ID creation policy resolved at compile time.
 * INFERRED preserves legacy 0.x conventions; choose explicitly for new entities.
 */
public enum IdStrategy {
    /** Backwards-compatible inference (UUID/String as UUIDv7; int/long as SQL identity). */
    INFERRED,
    /** Caller supplies every ID, including UUID, String and numeric identifiers. */
    ASSIGNED,
    /** Generate UUID version 7 in Java; only UUID and String IDs. */
    UUID_V7,
    /** Retrieve database-generated identity through JDBC generated keys; numeric IDs only. */
    IDENTITY
}
