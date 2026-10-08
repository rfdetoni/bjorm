package com.github.rfdetoni.bjorm;
import java.lang.annotation.*;
@Retention(RetentionPolicy.SOURCE) @Target(ElementType.PARAMETER)
public @interface Param { String value(); }
