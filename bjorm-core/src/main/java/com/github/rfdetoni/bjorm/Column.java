package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD})
public @interface Column { String value(); }
