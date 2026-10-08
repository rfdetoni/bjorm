package com.github.rfdetoni.bjorm;
import java.sql.*;
/** Stateless generated mapper. Bindings never use reflection on the hot path. */
public interface EntityMapper<T> extends RowMapper<T> {
    Class<T> type();
    String insertSql();
    String updateSql();
    /** PostgreSQL native INSERT ON CONFLICT (primary key) DO UPDATE. */
    default String upsertSql() { throw new UnsupportedOperationException("Upsert SQL not generated"); }
    default void bindUpsert(PreparedStatement ps,T entity) throws SQLException { bindInsert(ps, entity); }
    /** Runs before INSERT and UPSERT; generated POJO mappers can assign UUID v7 here. */
    default void prepareInsert(T entity) {}
    /** Return the insertable instance (records are copied; mutable POJOs are filled in-place). */
    default T materializeInsert(T entity) { prepareInsert(entity); return entity; }
    /** Reconstitute immutable records when the database returns an identity column. */
    default T withGeneratedId(ResultSet keys, T entity) throws SQLException { acceptGeneratedId(keys, entity); return entity; }
    /** Immutable entity instances need a return value when their key must be generated. */
    default boolean immutable() { return false; }
    default java.util.List<ChildRelation<T>> children() { return java.util.List.of(); }
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
    default boolean generatedId() { return false; }
    default void acceptGeneratedId(ResultSet keys,T entity) throws SQLException {}
    default boolean optimisticLocking() { return false; }
    default void bindDelete(PreparedStatement ps, T entity) throws SQLException { bindId(ps, 1, id(entity)); }
    default String table() { return ""; }
}
