package com.github.rfdetoni.bjorm;

/** Compile-time metadata for an eager many-to-one lookup without N+1 queries. */
public interface ParentRelation<T> {
    Class<?> targetType();
    String mappedBy();
    String property();
    default JoinType joinType(){return JoinType.LEFT;}
}
