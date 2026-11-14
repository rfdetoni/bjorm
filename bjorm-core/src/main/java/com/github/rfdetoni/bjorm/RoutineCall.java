package com.github.rfdetoni.bjorm;

import java.sql.SQLType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Positional JDBC callable routine with named result aliases. Names passed to in/out
 * are labels, NOT database parameter names; binding uses declared order.
 * Single-use and not thread-safe. The Operations instance owns connection/tenant scope.
 */
public final class RoutineCall {
    public enum Mode { IN, OUT, INOUT }
    public record Parameter(String name, Mode mode, Object value, SQLType type) {}

    private final Operations operations;
    private final String routine;
    private final boolean function;
    private final List<Parameter> parameters = new ArrayList<>();
    private final Set<String> names = new HashSet<>();
    private List<RowMapper<?>> mappers = List.of();
    private SQLType returnType;
    private boolean executed;

    RoutineCall(Operations operations, String routine, boolean function) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.routine = validateRoutine(routine);
        this.function = function;
    }

    private static String validateRoutine(String routine) {
        Objects.requireNonNull(routine, "routine");
        if (!routine.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)?"))
            throw new IllegalArgumentException("Unsafe routine identifier: " + routine);
        return routine;
    }

    private RoutineCall add(String name, Mode mode, Object value, SQLType type) {
        if (executed) throw new IllegalStateException("RoutineCall already executed");
        Objects.requireNonNull(name, "parameter name");
        if (!name.matches("[A-Za-z_][A-Za-z_0-9]*") || "return".equals(name) || !names.add(name))
            throw new IllegalArgumentException("Invalid or duplicate routine parameter: " + name);
        if (mode != Mode.IN) Objects.requireNonNull(type, "OUT parameter SQLType");
        if (type != null && type.getVendorTypeNumber() == null)
            throw new IllegalArgumentException("SQLType must have JDBC vendor type number");
        parameters.add(new Parameter(name, mode, value, type));
        return this;
    }

    public RoutineCall in(String name, Object value) { return add(name, Mode.IN, value, null); }
    /** Typed binding also supports NULL input values without JDBC type inference. */
    public RoutineCall in(String name, Object value, SQLType type) {
        return add(name, Mode.IN, value, Objects.requireNonNull(type, "type"));
    }
    public RoutineCall out(String name, SQLType type) { return add(name, Mode.OUT, null, type); }
    public RoutineCall inOut(String name, Object value, SQLType type) {
        return add(name, Mode.INOUT, value, type);
    }

    /** JDBC function return occupies position 1, exposed as result.get("return", type). */
    public RoutineCall returns(SQLType type) {
        if (!function || executed || returnType != null)
            throw new IllegalStateException("Return type can only be set once on a function");
        returnType = Objects.requireNonNull(type, "return type");
        if (type.getVendorTypeNumber() == null)
            throw new IllegalArgumentException("SQLType must have JDBC vendor type number");
        return this;
    }

    /**
     * Map each result set in order. With one mapper it is reused for all sets.
     * Without a mapper any returned ResultSet is an error, not silently discarded.
     */
    public RoutineCall rows(RowMapper<?>... readers) {
        if (executed) throw new IllegalStateException("RoutineCall already executed");
        Objects.requireNonNull(readers, "readers");
        if (readers.length == 0) throw new IllegalArgumentException("Supply at least one mapper");
        for (RowMapper<?> mapper : readers) Objects.requireNonNull(mapper, "row mapper");
        mappers = List.of(readers.clone());
        return this;
    }

    public RoutineResult execute() {
        if (executed) throw new IllegalStateException("RoutineCall already executed");
        if (function && returnType == null)
            throw new IllegalStateException("Function requires returns(SQLType)");
        executed = true;
        return operations.executeRoutine(this);
    }

    public String routine() { return routine; }
    public boolean isFunction() { return function; }
    public SQLType returnType() { return returnType; }
    public List<Parameter> parameters() { return List.copyOf(parameters); }
    public List<RowMapper<?>> mappers() { return mappers; }

    String sql(SqlDialect dialect) {
        String marks = String.join(", ", java.util.Collections.nCopies(parameters.size(), "?"));
        if (function) return "{? = call " + routine + "(" + marks + ")}";
        // pgJDBC defaults JDBC escape calls to SELECT (function), so a procedure must
        // use native CALL. MySQL supports standard JDBC escape procedure syntax.
        if ("postgresql".equals(dialect.name())) return "CALL " + routine + "(" + marks + ")";
        return "{call " + routine + "(" + marks + ")}";
    }
}
