package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;

/**
 * Flatten an immutable record value into the enclosing @Table columns.
 * All record fields must be persistent scalar values; default prefix is
 * the embedded Java property name followed by underscore.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.RECORD_COMPONENT,ElementType.FIELD})
public @interface Embedded {
    String prefix() default "";
}
