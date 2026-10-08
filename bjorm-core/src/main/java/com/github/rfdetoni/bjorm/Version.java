package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
/** Integer or long version for optimistic locking. */
@Retention(RetentionPolicy.SOURCE) @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD})
public @interface Version {}
