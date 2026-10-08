package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
@Retention(RetentionPolicy.SOURCE) @Target({ElementType.RECORD_COMPONENT,ElementType.FIELD})
public @interface Id { /** JDBC generated key; supported on mutable POJOs with setters. */ boolean generated() default false; /** Generate UUID v7 for mutable POJOs with a null ID. */ boolean uuidV7() default false; }
