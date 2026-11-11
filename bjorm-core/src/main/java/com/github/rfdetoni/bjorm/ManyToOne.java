package com.github.rfdetoni.bjorm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Eager reference to a single @Table entity. mappedBy is the Java name of
 * this entity's scalar foreign-key property, not a SQL column name.
 * Does not cascade persistence to the referenced entity.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD,ElementType.RECORD_COMPONENT})
public @interface ManyToOne {
    /** Single-FK Java property; use mappedByParts for composite target IDs. */
    String mappedBy() default "";
    /** FK Java properties in exactly the same order as target @Id declarations. */
    String[] mappedByParts() default {};
    JoinType type() default JoinType.LEFT;
}
