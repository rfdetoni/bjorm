package com.github.rfdetoni.bjorm;
/** Optional convenience: no static registry or implicit session. Works with Java records too. */
public interface ActiveRecord<T extends ActiveRecord<T>> {
    @SuppressWarnings("unchecked")
    default T insert(Bjorm db) { db.insert(this); return (T) this; }
    @SuppressWarnings("unchecked")
    default T update(Bjorm db) { db.update(this); return (T) this; }
    default T upsert(Bjorm db) { db.upsert(this); return (T) this; }
    default void delete(Bjorm db) { db.delete(this); }
}
