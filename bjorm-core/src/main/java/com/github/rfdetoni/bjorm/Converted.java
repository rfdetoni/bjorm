package com.github.rfdetoni.bjorm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt-in compile-time mapping of an immutable domain value to one JDBC column.
 * The converter must be public, thread-safe and have a public no-argument constructor.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Converted {
    Class<? extends JdbcColumnConverter<?>> value();
}
