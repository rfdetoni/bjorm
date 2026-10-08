package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
/** Compile-time positional reader for a Java record representing a SQL projection. */
@Retention(RetentionPolicy.SOURCE) @Target(ElementType.TYPE)
public @interface Projection {}
