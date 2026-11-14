package com.github.rfdetoni.bjorm;

/**
 * Shared deterministic aliases for compiler-generated paths, DSL queries and
 * eager graph joins. The root is t0; each JOIN gets the next free tN.
 * Numbers express query position, never a table or tenant name.
 */
public final class SqlAliases {
    private SqlAliases() {}

    public static String root() { return "t0"; }

    public static String at(int position) {
        if(position < 0) throw new IllegalArgumentException("Alias position must be non-negative");
        return "t" + position;
    }

    public static String explicit(String alias) {
        if(alias == null || !alias.matches("[A-Za-z_][A-Za-z_0-9]*"))
            throw new IllegalArgumentException("Invalid SQL alias: " + alias);
        return alias;
    }
}
