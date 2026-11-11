package com.github.rfdetoni.bjorm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Dialect-aware, parameterized JSON/JSONB predicates for mapped Field columns. */
public final class SqlJsonPredicates {
    private SqlJsonPredicates() {}

    /** Test containment of a JSON value; PostgreSQL @> and MySQL JSON_CONTAINS. */
    public static SqlPredicate contains(Field<?> column,SqlDialect dialect,String jsonValue) {
        Objects.requireNonNull(column,"column");
        Objects.requireNonNull(jsonValue,"jsonValue");
        return switch(Objects.requireNonNull(dialect,"dialect").name()) {
            case "postgresql" -> new SqlPredicate(
                column.column()+" @> CAST(? AS jsonb)",List.of(jsonValue));
            case "mysql" -> new SqlPredicate(
                "JSON_CONTAINS("+column.column()+", CAST(? AS JSON))",List.of(jsonValue));
            default -> throw new UnsupportedOperationException(
                "JSON containment unsupported by dialect: "+dialect.name());
        };
    }

    /**
     * Compare a text-valued JSON leaf using strictly bound path segments.
     * Only simple JSON object keys are supported; no arbitrary JSONPath syntax.
     * SQL NULL and JSON null do not compare equal to a non-null expected text.
     */
    public static SqlPredicate textEquals(Field<?> column,SqlDialect dialect,
                                           List<String> path,String expected) {
        Objects.requireNonNull(column,"column");
        Objects.requireNonNull(expected,"expected");
        Objects.requireNonNull(path,"path");
        if(path.isEmpty() || path.size()>16)
            throw new IllegalArgumentException("JSON path must contain 1..16 object-key segments");
        for(String segment:path)
            if(segment==null || !segment.matches("[A-Za-z_][A-Za-z_0-9]*"))
                throw new IllegalArgumentException("Invalid JSON path segment");
        return switch(Objects.requireNonNull(dialect,"dialect").name()) {
            case "postgresql" -> {
                ArrayList<Object> arguments=new ArrayList<>(path.size()+1);
                StringBuilder sql=new StringBuilder("jsonb_extract_path_text(")
                    .append(column.column());
                for(String segment:path) {
                    sql.append(", ?");
                    arguments.add(segment);
                }
                sql.append(") = ?");
                arguments.add(expected);
                yield new SqlPredicate(sql.toString(),arguments);
            }
            case "mysql" -> {
                // No user-controlled SQL: full JSON path is a single JDBC parameter.
                String jsonPath="$."+String.join(".",path);
                yield new SqlPredicate(
                    "JSON_UNQUOTE(JSON_EXTRACT("+column.column()+", ?)) = ?",
                    List.of(jsonPath,expected));
            }
            default -> throw new UnsupportedOperationException(
                "JSON path extraction unsupported by dialect: "+dialect.name());
        };
    }
}
