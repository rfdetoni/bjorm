package com.github.rfdetoni.bjorm;

import java.sql.*;
import java.util.List;
import java.util.UUID;

/** SQL/JDBC differences isolated from mapper and transaction logic. No driver dependency. */
public interface SqlDialect {
    String name();
    /** Cast a bound text parameter into the vendor's native JSON value. */
    String jsonParameter();
    /** Generates an atomic vendor-native primary-key upsert from compile-time metadata. */
    String upsertSql(String insertSql, String table, String idColumn,
                     List<String> updatedColumns, String versionColumn);
    /** Explicitly reject unsupported join kinds rather than sending broken SQL. */
    default String join(JoinType type) {return type.sql();}
    default String sql(String generated) {
        if("CAST(? AS jsonb)".equals(jsonParameter()) || !generated.contains("CAST(? AS jsonb)"))
            return generated;
        return generated.replace("CAST(? AS jsonb)",jsonParameter());
    }
    default void bindUuid(PreparedStatement ps,int position,UUID value)throws SQLException {
        ps.setObject(position,value);
    }
    default void bindValue(PreparedStatement ps,int position,Object value)throws SQLException {
        if(value instanceof UUID uuid)bindUuid(ps,position,uuid);
        else ps.setObject(position,value);
    }
}
