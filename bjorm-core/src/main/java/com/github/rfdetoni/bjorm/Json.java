package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
/** Maps a String containing JSON into PostgreSQL jsonb using CAST(? AS jsonb). */
@Retention(RetentionPolicy.SOURCE) @Target({ElementType.RECORD_COMPONENT,ElementType.FIELD})
public @interface Json {}
