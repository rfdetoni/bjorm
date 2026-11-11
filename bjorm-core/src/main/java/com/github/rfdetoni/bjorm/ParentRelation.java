package com.github.rfdetoni.bjorm;

/** Compile-time metadata for an eager many-to-one lookup without N+1 queries. */
public interface ParentRelation<T> {
    Class<?> targetType();
    String mappedBy();
    /** Ordered FK Java properties; legacy single-column relations use mappedBy(). */
    default java.util.List<String> mappedByParts(){return java.util.List.of(mappedBy());}
    String property();
    default JoinType joinType(){return JoinType.LEFT;}
}
