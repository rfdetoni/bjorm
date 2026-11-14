package com.github.rfdetoni.bjorm;

import java.util.Objects;

/**
 * Compile-time generated entity path contract. A path contains only its Java
 * entity type and a validated SQL alias; it never owns a JDBC connection.
 */
public interface EntityPath<T> {
    Class<T> type();
    String alias();
    /** Return the same generated path type qualified by an explicit alias. */
    EntityPath<T> as(String alias);

    /** Use the generated metamodel with the regular explicit BJORM selector. */
    default Select<T> select(Operations db) {
        return Objects.requireNonNull(db, "db").select(type()).as(alias());
    }
}
