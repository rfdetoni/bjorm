package com.github.rfdetoni.bjorm;
import java.util.*;
/** Generated type-safe SQL field; column identifiers are validated, values always bound. */
public final class Field<T> {
    private final String column;
    public Field(String column) {
        if (column == null || !column.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)?"))
            throw new IllegalArgumentException("Invalid SQL identifier: " + column);
        this.column = column;
    }
    public String column() { return column; }
    public Field<T> as(String alias) {
        if(!alias.matches("[A-Za-z_][A-Za-z_0-9]*") || column.contains("."))
            throw new IllegalArgumentException("Invalid alias or already-qualified column");
        return new Field<>(alias+"."+column);
    }
    /** Typed join equality comparing two column paths, not a bound value. */
    public SqlPredicate sameAs(Field<T> other) {
        return new SqlPredicate(column+" = "+Objects.requireNonNull(other).column, List.of());
    }
    public SqlPredicate eq(T value) { return value == null ? new SqlPredicate(column + " IS NULL", List.of()) : bind("=", value); }
    public SqlPredicate ne(T value) { return value == null ? new SqlPredicate(column + " IS NOT NULL", List.of()) : bind("<>", value); }
    public SqlPredicate gt(T value) { return bind(">", value); }
    public SqlPredicate gte(T value) { return bind(">=", value); }
    public SqlPredicate lt(T value) { return bind("<", value); }
    public SqlPredicate lte(T value) { return bind("<=", value); }
    public SqlPredicate like(String pattern) { return bind("LIKE", pattern); }
    public SqlPredicate between(T low, T high) { return new SqlPredicate(column + " BETWEEN ? AND ?", Arrays.asList(Objects.requireNonNull(low),Objects.requireNonNull(high))); }
    public SqlPredicate isNull() { return new SqlPredicate(column + " IS NULL", List.of()); }
    public SqlPredicate isNotNull() { return new SqlPredicate(column + " IS NOT NULL", List.of()); }
    public SqlPredicate in(Collection<? extends T> values) {
        Objects.requireNonNull(values);
        if (values.isEmpty()) return new SqlPredicate("1 = 0", List.of());
        if (values.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("IN does not accept null values; use isNull() explicitly");
        return new SqlPredicate(column + " IN (" + String.join(", ", Collections.nCopies(values.size(), "?")) + ")", new ArrayList<>(values));
    }
    public SqlOrder asc() { return new SqlOrder(column, false); }
    public SqlOrder desc() { return new SqlOrder(column, true); }
    private SqlPredicate bind(String op,Object value) { return new SqlPredicate(column + " " + op + " ?", Collections.singletonList(Objects.requireNonNull(value))); }
}
