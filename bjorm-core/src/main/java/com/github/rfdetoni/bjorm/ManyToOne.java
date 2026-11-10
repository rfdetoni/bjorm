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
    String mappedBy();
    JoinType type() default JoinType.LEFT;
}
