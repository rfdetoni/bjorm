package com.github.rfdetoni.bjorm;
/** PostgreSQL join types for generated relations and the typed SQL DSL. */
public enum JoinType {
    INNER("INNER JOIN"),
    LEFT("LEFT JOIN"),
    RIGHT("RIGHT JOIN"),
    FULL("FULL OUTER JOIN");
    private final String sql;
    JoinType(String sql) {this.sql=sql;}
    public String sql() {return sql;}
}
