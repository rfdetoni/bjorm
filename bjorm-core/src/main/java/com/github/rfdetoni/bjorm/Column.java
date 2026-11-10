package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
/** Compile-time column mapping and safe INSERT/UPDATE participation policy. */
@Retention(RetentionPolicy.CLASS) @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD})
public @interface Column {
    String value();
    /** False for computed/database-populated columns. */
    boolean insertable() default true;
    /** False for read-only columns; native updateBy also rejects them. */
    boolean updatable() default true;
}
