package com.github.rfdetoni.bjorm;
import java.util.*;
/** Common entry points for database and explicit transaction; generated repositories use this contract. */
public interface Operations {
    /** Selected dialect for compiled native @Query binders. */
    SqlDialect dialect();
    /** Snapshot values and bind them through the active dialect (UUID, Instant, etc).
     * Use with query/one/execute. Does not expose a connection or execute SQL. */
    default StatementBinder params(Object... values) {
        Object[] captured = Objects.requireNonNull(values, "values").clone();
        SqlDialect active = Objects.requireNonNull(dialect(), "dialect");
        return ps -> {
            for (int i=0; i<captured.length; i++)
                active.bindValue(ps, i+1, captured[i]);
        };
    }
    <T> void insert(T entity);
    /** Persist and return the entity with its generated ID; required for immutable records. */
    <T> T insertReturning(T entity);
    /**
     * Insert a detached generated copy, leaving the caller-owned mutable POJO unchanged
     * on success, failed JDBC writes and transaction rollback. Child graphs are unsupported.
     * The returned entity is a proposed persisted shape, not a commit confirmation when
     * called inside an externally managed transaction.
     */
    <T> T insertDetached(T entity);
    /** UPSERT with the same non-mutating caller contract as insertDetached. */
    <T> T upsertDetached(T entity);
    /** Return generated record IDs after a batch; numeric database identity uses per-row inserts. */
    <T> List<T> batchInsertReturning(List<T> entities);
    /** Return an ID-bearing record after a PostgreSQL upsert. */
    <T> T upsertReturning(T entity);
    <T> int update(T entity);
    /** Atomic PostgreSQL insert-or-update by primary key. */
    <T> int upsert(T entity);
    <T> int delete(T entity);
    <T> T find(Class<T> type, Object id);
    <T> List<T> list(Class<T> type, SqlPredicate predicate);
    /** SQL COUNT(*) over a mapped table and typed predicate; does not materialize entities. */
    <T> long count(Class<T> type, SqlPredicate predicate);
    /** SQL SELECT 1 WHERE ... LIMIT 1; uses short-circuit index access when possible. */
    <T> boolean exists(Class<T> type, SqlPredicate predicate);
    /** Lock at most one matched row in an existing transaction. Returns false if absent. */
    <T> boolean lockOne(Class<T> type, SqlPredicate predicate, LockMode mode);
    /** Lock up to limit flat rows in primary-key order inside the caller-owned transaction.
     * SKIP_LOCKED results are suitable for concurrent queues, not a consistent snapshot. */
    default <T> List<T> lockRows(Class<T> type, SqlPredicate predicate, LockMode mode, int limit) {
        throw new UnsupportedOperationException("Bounded row locking is not supported by this Operations implementation");
    }
    default <T> long count(Class<T> type) { return count(type, null); }
    default <T> boolean exists(Class<T> type) { return exists(type, null); }
    <T> List<T> list(Select<T> query);
    <T> Optional<T> first(Select<T> query);
    <T> List<Map<String,Object>> fieldRows(Select<T> query,String[] properties);
    <T> Optional<Map<String,Object>> fieldOne(Select<T> query,String[] properties);
    <T,P> List<P> project(Select<T> query,String[] properties,RowMapper<P> projection);
    <T,P> Optional<P> projectOne(Select<T> query,String[] properties,RowMapper<P> projection);
    <T,C> List<C> columnValues(Select<T> query,String property,Class<C> type);
    <T,C> Optional<C> columnOne(Select<T> query,String property,Class<C> type);
    <T> List<T> query(String sql, StatementBinder binder, RowMapper<T> mapper);
    <T> T one(String sql, StatementBinder binder, RowMapper<T> mapper);
    int execute(String sql, StatementBinder binder);
    <T> void forEach(Class<T> type, SqlPredicate predicate, java.util.function.Consumer<T> consumer);
    <T> void forEach(Select<T> query, java.util.function.Consumer<T> consumer);
    /**
     * Resource-scoped, sequential cursor stream. The JDBC ResultSet and
     * PreparedStatement close when the callback returns or throws.
     * Do not retain the stream past the callback. Only flat mapped entities
     * are supported; graph relations require forEach with eager hydration.
     */
    <T,R> R withStream(Select<T> query,
        java.util.function.Function<java.util.stream.Stream<T>,R> work);
    default <T,R> R withStream(Class<T> type,SqlPredicate where,
            java.util.function.Function<java.util.stream.Stream<T>,R> work) {
        return withStream(select(type).whereNullable(where),work);
    }
    <T> void scan(String sql, StatementBinder binder, RowMapper<T> mapper, java.util.function.Consumer<T> consumer);
    <T> void batchInsert(List<T> entities);
    <T> void batchUpdate(List<T> entities);
    /** Delete graphs in a single transaction, checking optimistic versions per entity. */
    <T> void batchDelete(List<T> entities);
    /** Atomic batch UPSERT; returns generated IDs and reloaded versioned entities. */
    <T> List<T> batchUpsertReturning(List<T> entities);
    /** Atomic per-row updates with reloaded versions; unlike JDBC executeBatch, returns database state. */
    <T> List<T> batchUpdateReturning(List<T> entities);
    /** Bulk operation using only compile-time mapped property identifiers. Requires a predicate. */
    <T> int updateBy(Class<T> type, Map<String,?> changes, SqlPredicate predicate);
    /** Bulk delete; deliberately disallowed for mapped child graphs or versioned entities. */
    <T> int deleteBy(Class<T> type, SqlPredicate predicate);
    /** Update and re-fetch the committed shape (including incremented @Version). */
    <T> T updateReturning(T entity);
    default <T> void batchUpsert(List<T> entities) {
        Objects.requireNonNull(entities);
        for (T entity : entities) {
            Objects.requireNonNull(entity);
            // Always use batchUpsertReturning when newly generated IDs must be observed.
        }
        batchUpsertReturning(entities);
    }
    default <T> List<T> list(Class<T> type) { return list(type, null); }
    default <T> Select<T> select(Class<T> type) { return new Select<>(this, type); }
    default <T> Optional<T> findOne(Class<T> type, SqlPredicate where) {
        return select(type).whereNullable(where).first();
    }
    default <T> Optional<T> findOne(Class<T> type) { return findOne(type,null); }
    default <T> List<Map<String,Object>> findFields(Class<T> type, SqlPredicate where,String... properties) {
        return select(type).whereNullable(where).fields(properties).fetch();
    }
    default <T> List<Map<String,Object>> findFields(Class<T> type,String... properties) {
        return findFields(type,null,properties);
    }
    default <T> Optional<Map<String,Object>> findOneFields(Class<T> type,SqlPredicate where,String... properties) {
        return select(type).whereNullable(where).fields(properties).first();
    }
    default <T> Optional<Map<String,Object>> findOneFields(Class<T> type,String... properties) {
        return findOneFields(type,null,properties);
    }
    default <T,C> List<C> findColumn(Class<T> type,String property,Class<C> valueType,SqlPredicate where) {
        return columnValues(select(type).whereNullable(where),property,valueType);
    }
    default <T,C> List<C> findColumn(Class<T> type,String property,Class<C> valueType) {
        return findColumn(type,property,valueType,null);
    }
    default <T,C> Optional<C> findColumnOne(Class<T> type,String property,Class<C> valueType,SqlPredicate where) {
        return columnOne(select(type).whereNullable(where),property,valueType);
    }
    default <T,C> Optional<C> findColumnOne(Class<T> type,String property,Class<C> valueType) {
        return findColumnOne(type,property,valueType,null);
    }
    default <T> List<T> query(String sql, RowMapper<T> mapper) { return query(sql, StatementBinder.NONE, mapper); }
    default <T> T one(String sql, RowMapper<T> mapper) { return one(sql, StatementBinder.NONE, mapper); }
}
