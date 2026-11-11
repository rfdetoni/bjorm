package com.github.rfdetoni.bjorm;

import java.util.Objects;

/**
 * Small typed, parameterized subquery predicates. Correlation uses generated
 * Fields with explicit aliases, never caller-provided SQL fragments.
 *
 * Only unqualified compiled tables are accepted: this prevents a subquery
 * from bypassing tenant-required schema/catalog scoping.
 */
public final class SqlSubqueries {
    private SqlSubqueries() {}

    public static SqlPredicate exists(EntityMapper<?> source,String alias,SqlPredicate condition) {
        return quantified(false,source,alias,condition);
    }

    public static SqlPredicate notExists(EntityMapper<?> source,String alias,SqlPredicate condition) {
        return quantified(true,source,alias,condition);
    }

    public static <T> SqlPredicate in(Field<T> outer,EntityMapper<?> source,
                                      String alias,Field<T> selected,SqlPredicate condition) {
        Objects.requireNonNull(outer,"outer");
        Objects.requireNonNull(selected,"selected");
        Objects.requireNonNull(condition,"condition");
        String table=table(source);
        String safeAlias=alias(alias);
        if(!selected.column().startsWith(safeAlias+"."))
            throw new IllegalArgumentException("Subquery projection must belong to its declared alias");
        return new SqlPredicate(outer.column()+" IN (SELECT "+selected.column()+" FROM "+
            table+" "+safeAlias+" WHERE "+condition.sql()+")",condition.params());
    }

    private static SqlPredicate quantified(boolean negated,EntityMapper<?> source,
                                           String alias,SqlPredicate condition) {
        Objects.requireNonNull(condition,"condition");
        String sql=(negated?"NOT EXISTS":"EXISTS")+" (SELECT 1 FROM "+
            table(source)+" "+alias(alias)+" WHERE "+condition.sql()+")";
        return new SqlPredicate(sql,condition.params());
    }

    private static String table(EntityMapper<?> source) {
        EntityMapper<?> mapper=Objects.requireNonNull(source,"source");
        String table=Objects.requireNonNull(mapper.table(),"table");
        if(!table.matches("[A-Za-z_][A-Za-z_0-9]*") ||
           mapper.staticSchema()==null || !mapper.staticSchema().isEmpty())
            throw new IllegalArgumentException(
                "Subqueries require an unqualified compiled table (no static schema)");
        return table;
    }
    private static String alias(String alias) {
        if(alias==null || !alias.matches("[A-Za-z_][A-Za-z_0-9]*"))
            throw new IllegalArgumentException("Invalid subquery alias");
        return alias;
    }
}
