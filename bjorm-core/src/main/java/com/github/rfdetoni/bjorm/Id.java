package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
/** IDs are inferred by type: UUID (v7), String (v7 text) or database identity (int/long).
 * Use assigned=true when the caller always supplies the primary key. */
@Retention(RetentionPolicy.CLASS) @Target({ElementType.RECORD_COMPONENT,ElementType.FIELD})
public @interface Id {
    /** Request a JDBC-generated key, normally numeric. Retained for explicit configurations. */
    boolean generated() default false;
    /** Explicit UUID v7 generation; inferred for UUID/String by default. */
    boolean uuidV7() default false;
    /** Disable automatic ID assignment (e.g. imported IDs). */
    boolean assigned() default false;
}
