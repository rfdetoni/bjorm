package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
@Retention(RetentionPolicy.SOURCE) @Target(ElementType.TYPE)
public @interface Table {
    String value();
    /** Optional fixed schema/database identifier, compiled into SQL (never request-controlled). */
    String schema() default "";
}
