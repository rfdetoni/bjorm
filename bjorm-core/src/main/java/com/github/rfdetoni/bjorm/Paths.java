package com.github.rfdetoni.bjorm;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Small typed paths emitted by the annotation processor in W* metamodels.
 * They are JDBC-independent and immutable; no proxies, AST or entity session.
 */
public final class Paths {
    private Paths() {}

    public static final class StringPath extends Field<String> {
        public StringPath(String column) { super(column); }
        public StringPath(String column, Function<? super String, ?> converter) { super(column, converter); }
        @Override public StringPath as(String alias) { return new StringPath(qualified(alias), converter()); }

        /** Escaped literal substring search; %, _ and ! are data, not wildcards. */
        public SqlPredicate contains(String fragment) { return match(fragment, "%", "%"); }
        public SqlPredicate startsWith(String prefix) { return match(prefix, "", "%"); }
        public SqlPredicate endsWith(String suffix) { return match(suffix, "%", ""); }

        private SqlPredicate match(String value, String before, String after) {
            Objects.requireNonNull(value, "value");
            String escaped = value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
            return new SqlPredicate(column() + " LIKE ? ESCAPE '!'", List.of(before + escaped + after));
        }
    }

    public static final class NumberPath<N extends Number> extends Field<N> {
        public NumberPath(String column) { super(column); }
        public NumberPath(String column, Function<? super N, ?> converter) { super(column, converter); }
        @Override public NumberPath<N> as(String alias) { return new NumberPath<>(qualified(alias), converter()); }
    }

    public static final class UuidPath extends Field<UUID> {
        public UuidPath(String column) { super(column); }
        @Override public UuidPath as(String alias) { return new UuidPath(qualified(alias)); }
    }

    public static final class BooleanPath extends Field<Boolean> {
        public BooleanPath(String column) { super(column); }
        @Override public BooleanPath as(String alias) { return new BooleanPath(qualified(alias)); }
        public SqlPredicate isTrue() { return eq(true); }
        public SqlPredicate isFalse() { return eq(false); }
    }

    public static final class EnumPath<E extends Enum<E>> extends Field<E> {
        public EnumPath(String column) { super(column); }
        @Override public EnumPath<E> as(String alias) { return new EnumPath<>(qualified(alias)); }
    }

    public static final class TemporalPath<T> extends Field<T> {
        public TemporalPath(String column) { super(column); }
        @Override public TemporalPath<T> as(String alias) { return new TemporalPath<>(qualified(alias)); }
    }
}
