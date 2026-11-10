package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
/** Opt-in ordinal enum mapping; do not reorder enum members after persisting data. */
@Retention(RetentionPolicy.CLASS) @Target({ElementType.RECORD_COMPONENT,ElementType.FIELD})
public @interface EnumOrdinal {}
