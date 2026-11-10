package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
/** Exclude a field or record component from SQL persistence and column mapping. */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD,ElementType.RECORD_COMPONENT})
public @interface Transient {}
