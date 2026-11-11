package com.github.rfdetoni.bjorm;

import java.lang.annotation.*;

/** Explicit one-to-many persistence relation, not a database column.
 * Child @Table type must expose a scalar FK Java property named mappedBy. */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Children {
    /** Single FK property; use mappedByParts for a composite parent primary key. */
    String mappedBy() default "";
    /** Child FK property names ordered to match every parent @Id. */
    String[] mappedByParts() default {};
    /** LEFT (default) preserves parents with no children. INNER filters those parents out of find/list. */
    JoinType type() default JoinType.LEFT;
}
