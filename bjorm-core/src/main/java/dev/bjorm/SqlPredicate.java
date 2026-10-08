package dev.bjorm;
import java.util.*;
public final class SqlPredicate {
    private final String sql;
    private final List<Object> params;
    SqlPredicate(String sql, List<Object> params) { this.sql = sql; this.params = Collections.unmodifiableList(new ArrayList<>(params)); }
    public String sql() { return sql; }
    public List<Object> params() { return params; }
    public SqlPredicate and(SqlPredicate other) { return combine("AND", other); }
    public SqlPredicate or(SqlPredicate other) { return combine("OR", other); }
    public SqlPredicate not() { return new SqlPredicate("NOT (" + sql + ")", params); }
    private SqlPredicate combine(String op, SqlPredicate other) {
        Objects.requireNonNull(other);
        ArrayList<Object> args = new ArrayList<>(params.size() + other.params.size());
        args.addAll(params); args.addAll(other.params);
        return new SqlPredicate("(" + sql + " " + op + " " + other.sql + ")", args);
    }
}
