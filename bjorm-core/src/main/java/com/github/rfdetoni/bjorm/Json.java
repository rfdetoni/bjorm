package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
/** Maps a String containing JSON to a dialect-native JSON column via a bound placeholder. */
@Retention(RetentionPolicy.SOURCE) @Target({ElementType.RECORD_COMPONENT,ElementType.FIELD})
public @interface Json {}
