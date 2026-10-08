package dev.bjorm;
import java.sql.*;
/** Stateless generated mapper. Bindings never use reflection on the hot path. */
public interface EntityMapper<T> extends RowMapper<T> {
    Class<T> type();
    String insertSql();
    String updateSql();
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
    default boolean generatedId() { return false; }
    default void acceptGeneratedId(ResultSet keys,T entity) throws SQLException {}
    default boolean optimisticLocking() { return false; }
    default void bindDelete(PreparedStatement ps, T entity) throws SQLException { bindId(ps, 1, id(entity)); }
    default String table() { return ""; }
}
