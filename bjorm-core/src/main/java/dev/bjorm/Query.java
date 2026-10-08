package dev.bjorm;
import java.lang.annotation.*;
/** SQL is translated into JDBC placeholders at compilation, not parsed at runtime. */
@Retention(RetentionPolicy.SOURCE) @Target(ElementType.METHOD)
public @interface Query { String value(); }
