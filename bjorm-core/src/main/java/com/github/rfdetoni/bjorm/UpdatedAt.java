package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
/** Set update time through the generated mapper on UPDATE and UPSERT. */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.RECORD_COMPONENT,ElementType.FIELD})
public @interface UpdatedAt {}
