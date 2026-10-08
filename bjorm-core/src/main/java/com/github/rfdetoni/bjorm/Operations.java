package com.github.rfdetoni.bjorm;
import java.util.*;
/** Common entry points for database and explicit transaction; generated repositories use this contract. */
public interface Operations {
    <T> void insert(T entity);
    <T> int update(T entity);
    /** Atomic PostgreSQL insert-or-update by primary key. */
    <T> int upsert(T entity);
    <T> int delete(T entity);
    <T> T find(Class<T> type, Object id);
    <T> List<T> list(Class<T> type, SqlPredicate predicate);
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
    <T> void scan(String sql, StatementBinder binder, RowMapper<T> mapper, java.util.function.Consumer<T> consumer);
    <T> void batchInsert(List<T> entities);
    <T> void batchUpdate(List<T> entities);
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
