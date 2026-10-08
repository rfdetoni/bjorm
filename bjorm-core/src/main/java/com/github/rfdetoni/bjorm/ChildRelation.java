package com.github.rfdetoni.bjorm;

/** Compile-time generated relationship wiring. No reflection or global state. */
public interface ChildRelation<P> {
    Class<?> childType();
    String mappedBy();
    Iterable<?> children(P parent);
    /** Attach/verify child FK before saving, without a dynamic property lookup. */
    void attach(Object parentId, Object child);
}
