package com.github.rfdetoni.bjorm;
import java.util.*;
/** Generated type-safe SQL field; column identifiers are validated, values always bound. */
public class Field<T> {
    private final String column;
    private final java.util.function.Function<? super T, ?> converter;
    public Field(String column) { this(column, null); }
    public Field(String column, java.util.function.Function<? super T, ?> converter) {
        if (column == null || !column.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)?"))
            throw new IllegalArgumentException("Invalid SQL identifier: " + column);
        this.column = column;
        this.converter = converter;
    }
    public String column() { return column; }
    protected final java.util.function.Function<? super T, ?> converter() { return converter; }
    protected final String qualified(String alias) {
        if(alias == null || !alias.matches("[A-Za-z_][A-Za-z_0-9]*") || column.contains("."))
            throw new IllegalArgumentException("Invalid alias or already-qualified column");
        return alias + "." + column;
    }
    public Field<T> as(String alias) { return new Field<>(qualified(alias), converter); }
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
    public SqlPredicate like(String pattern) { return new SqlPredicate(column + " LIKE ?", Collections.singletonList(Objects.requireNonNull(pattern))); }
    public SqlPredicate between(T low, T high) { return new SqlPredicate(column + " BETWEEN ? AND ?", Arrays.asList(convert(Objects.requireNonNull(low)),convert(Objects.requireNonNull(high)))); }
    public SqlPredicate isNull() { return new SqlPredicate(column + " IS NULL", List.of()); }
    public SqlPredicate isNotNull() { return new SqlPredicate(column + " IS NOT NULL", List.of()); }
    public SqlPredicate in(Collection<? extends T> values) {
        Objects.requireNonNull(values);
        if (values.isEmpty()) return new SqlPredicate("1 = 0", List.of());
        if (values.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("IN does not accept null values; use isNull() explicitly");
        ArrayList<Object> bound = new ArrayList<>(values.size());
        for (T value : values) bound.add(convert(value));
        return new SqlPredicate(column + " IN (" + String.join(", ", Collections.nCopies(values.size(), "?")) + ")", bound);
    }
    public SqlOrder asc() { return new SqlOrder(column, false); }
    public SqlOrder desc() { return new SqlOrder(column, true); }
    private Object convert(T value) { return converter == null ? value : converter.apply(value); }
    private SqlPredicate bind(String op,T value) { return new SqlPredicate(column + " " + op + " ?", Collections.singletonList(convert(Objects.requireNonNull(value)))); }
}
