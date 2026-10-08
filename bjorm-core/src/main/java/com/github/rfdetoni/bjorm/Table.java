package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
@Retention(RetentionPolicy.SOURCE) @Target(ElementType.TYPE)
public @interface Table { String value(); }
