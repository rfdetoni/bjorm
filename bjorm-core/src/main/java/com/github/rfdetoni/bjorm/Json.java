package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
/** Typed JSON column. Strings need no codec; structured fields use user-provided codecs. */
@Retention(RetentionPolicy.CLASS) @Target({ElementType.RECORD_COMPONENT,ElementType.FIELD})
public @interface Json {
    Class<? extends JsonCodec<?>> codec() default JsonStringCodec.class;
}
