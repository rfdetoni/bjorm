package com.github.rfdetoni.bjorm;

import java.lang.annotation.*;

/** Explicit one-to-many persistence relation, not a database column.
 * Child @Table type must expose a scalar FK Java property named mappedBy. */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Children {
    String mappedBy();
    /** Default eager join strategy; LEFT preserves roots with no children. */
    JoinType type() default JoinType.LEFT;
}
