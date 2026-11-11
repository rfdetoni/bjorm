package com.github.rfdetoni.bjorm;
import java.sql.*;
/** Stateless generated mapper. Bindings never use reflection on the hot path. */
public interface EntityMapper<T> extends RowMapper<T> {
    Class<T> type();
    String insertSql();
    String updateSql();
    /** PostgreSQL native INSERT ON CONFLICT (primary key) DO UPDATE. */
    default String upsertSql() { throw new UnsupportedOperationException("Upsert SQL not generated"); }
    default String upsertSql(SqlDialect dialect){return upsertSql();}
    default String insertSql(SqlDialect dialect){return dialect.sql(insertSql());}
    default String updateSql(SqlDialect dialect){return dialect.sql(updateSql());}
    default String deleteSql(SqlDialect dialect){return deleteSql();}
    default String selectSql(SqlDialect dialect){return selectSql();}
    default void bindInsert(PreparedStatement ps,T value,SqlDialect dialect)throws SQLException{bindInsert(ps,value);}
    default void bindUpdate(PreparedStatement ps,T value,SqlDialect dialect)throws SQLException{bindUpdate(ps,value);}
    default void bindDelete(PreparedStatement ps,T value,SqlDialect dialect)throws SQLException{bindDelete(ps,value);}
    default void bindId(PreparedStatement ps,int index,Object id,SqlDialect dialect)throws SQLException {
        dialect.bindValue(ps,index,id);
    }
    default void bindUpsert(PreparedStatement ps,T value,SqlDialect dialect)throws SQLException{bindInsert(ps,value,dialect);}
    default void bindUpsert(PreparedStatement ps,T entity) throws SQLException { bindInsert(ps, entity); }
    /** Runs before INSERT and UPSERT; generated POJO mappers can assign UUID v7 here. */
    default void prepareInsert(T entity) {}
    /** Return the insertable instance (records are copied; mutable POJOs are filled in-place). */
    default T materializeInsert(T entity) { prepareInsert(entity); return entity; }
    /** Snapshot a mutable entity for detached writes. Generated POJO mappers copy persisted properties; records are immutable. */
    default T copyForWrite(T entity) {
        if (immutable()) return entity;
        throw new UnsupportedOperationException("Detached write requires a generated copyForWrite mapper");
    }
    /** Stamp @UpdatedAt on UPDATE without reflection. */
    default T materializeUpdate(T entity) { return entity; }
    /** Stamp @UpdatedAt on UPSERT and retain the insert-time @CreatedAt behavior. */
    default T materializeUpsert(T entity) { return materializeInsert(entity); }
    /** Generated timestamp column for safe set-based UPDATE operations. */
    default String updatedAtProperty() { return null; }
    default Object updatedAtNow() { return null; }
    /** Reconstitute immutable records when the database returns an identity column. */
    default T withGeneratedId(ResultSet keys, T entity) throws SQLException { acceptGeneratedId(keys, entity); return entity; }
    /** Immutable entity instances need a return value when their key must be generated. */
    default boolean immutable() { return false; }
    default java.util.List<ChildRelation<T>> children() { return java.util.List.of(); }
    /** Read-only eager references assembled by the JOIN result mapper. */
    default java.util.List<ParentRelation<T>> parents() { return java.util.List.of(); }
    /** Generated POJO setter or immutable record constructor. */
    default T withParent(T entity,String property,Object parent) {
        throw new UnsupportedOperationException("Eager to-one mapper not generated");
    }
    /** Replace immutable record's @Children collection with persisted child copies. */
    default T withChildren(T entity,String relation,java.util.List<?> children) { return entity; }
    /** Generated positional mapper for joined rows, at 1-based JDBC column offset. */
    default T readAt(ResultSet rs, int offset) throws SQLException {
        if (offset != 1) throw new UnsupportedOperationException("Joined row reader not generated");
        return read(rs);
    }
    /** Exact count/position of persistent SQL columns, excluding @Children. */
    default int columnCount() { throw new UnsupportedOperationException("Column count not generated"); }
    default int idColumnIndex() { throw new UnsupportedOperationException("ID column index not generated"); }
    default int columnIndex(String sqlColumn) { throw new UnsupportedOperationException("Column index not generated"); }
    String selectSql();
    String deleteSql();
    String selectAllSql();
    /** Map a Java property (not an arbitrary SQL fragment) to its generated database column. */
    default String columnFor(String property) {
        throw new IllegalArgumentException("Unknown mapped property: " + property);
    }
    /** Read one selected property without constructing a partially populated entity. */
    default Object readProperty(ResultSet rs, int index, String property) throws SQLException {
        throw new IllegalArgumentException("Unknown mapped property: " + property);
    }
    /** Qualified positional select fields for explicit DSL joins. */
    default String qualifiedColumns(String alias) { throw new UnsupportedOperationException("Join mapper not generated"); }
    void bindInsert(PreparedStatement ps, T entity) throws SQLException;
    void bindUpdate(PreparedStatement ps, T entity) throws SQLException;
    void bindId(PreparedStatement ps, int index, Object id) throws SQLException;
    Object id(T entity);
    /** Java name of the generated primary-key property. */
    default String idProperty() { return "id"; }
    /** Ordered generated key property names used by composite FK JOINs. */
    default java.util.List<String> idProperties() { return java.util.List.of(idProperty()); }
    /** Multiple assigned @Id properties; generated mapper binds every component. */
    default boolean compositeId() {return false;}
    default boolean isIdProperty(String property) {return idProperty().equals(property);}
    default boolean generatedId() { return false; }
    default void acceptGeneratedId(ResultSet keys,T entity) throws SQLException {}
    default boolean optimisticLocking() { return false; }
    /** Generated optimistic version column, used to classify MySQL CAS failures. */
    default String versionColumn() { return null; }
    /** Version captured in the submitted entity; no reflective field access. */
    default Object versionValue(T entity) { return null; }
    /** UPSERT must re-read DB-generated/immutable columns instead of returning stale caller values. */
    default boolean upsertNeedsReload() { return optimisticLocking(); }
    default void bindDelete(PreparedStatement ps, T entity) throws SQLException { bindId(ps, 1, id(entity)); }
    /** Allow generator to emit vendor JSON placeholder casts, no reflection. */
    default String bulkParameterSql(String property,SqlDialect dialect) {
        columnFor(property);
        return "?";
    }
    /** Encode generated custom JSON fields before binding through a safe prepared statement. */
    default Object bulkParameterValue(String property,Object value) {
        columnFor(property);
        return value;
    }
    /** Fixed schema selected at compilation; never interchangeable with runtime tenant routing. */
    default String staticSchema() { return ""; }
    default String table() { return ""; }
}
