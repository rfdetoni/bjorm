package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
/** Fill missing creation time at insertion. Kept immutable by generated UPDATE and UPSERT SQL. */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.RECORD_COMPONENT,ElementType.FIELD})
public @interface CreatedAt {}
