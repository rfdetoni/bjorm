package dev.bjorm;
import java.lang.annotation.*;
@Retention(RetentionPolicy.SOURCE) @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD})
public @interface Column { String value(); }
