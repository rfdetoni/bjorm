package dev.bjorm;
/** Ordering based on a generated, safe SQL field. */
public record SqlOrder(String column, boolean descending) {
    public SqlOrder {
        if (column == null || !column.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)?")) throw new IllegalArgumentException("Unsafe column: "+column);
    }
    public String sql() { return column + (descending ? " DESC" : " ASC"); }
}
