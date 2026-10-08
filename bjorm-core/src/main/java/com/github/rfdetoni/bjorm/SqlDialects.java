package com.github.rfdetoni.bjorm;

import java.sql.*;
import java.util.*;

/** Supported built-in SQL dialects; applications may supply their own SqlDialect. */
public final class SqlDialects {
    private SqlDialects(){}
    public static final SqlDialect POSTGRESQL = new PostgreSql();
    public static final SqlDialect MYSQL = new MySql();

    public static SqlDialect named(String name) {
        return switch(Objects.requireNonNull(name).strip().toLowerCase(Locale.ROOT)) {
            case "postgres", "postgresql" -> POSTGRESQL;
            case "mysql", "mysql8" -> MYSQL;
            default -> throw new IllegalArgumentException("Unsupported BJORM dialect: "+name);
        };
    }

    private static final class PostgreSql implements SqlDialect {
        public String name(){return "postgresql";}
        public String jsonParameter(){return "CAST(? AS jsonb)";}
        public String upsertSql(String insertSql,String table,String idColumn,
                                List<String> columns,String versionColumn) {
            if(columns.isEmpty())return insertSql+" ON CONFLICT ("+idColumn+") DO NOTHING";
            StringJoiner changes=new StringJoiner(", ");
            for(String column:columns) {
                changes.add(column+" = "+(column.equals(versionColumn)
                    ? table+"."+column+" + 1" : "EXCLUDED."+column));
            }
            return insertSql+" ON CONFLICT ("+idColumn+") DO UPDATE SET "+changes+
                (versionColumn==null?"":" WHERE "+table+"."+versionColumn+" = EXCLUDED."+versionColumn);
        }
    }
    private static final class MySql implements SqlDialect {
        public String name(){return "mysql";}
        public String jsonParameter(){return "CAST(? AS JSON)";}
        public String join(JoinType type) {
            if(type==JoinType.FULL)
                throw new UnsupportedOperationException("MySQL does not support FULL OUTER JOIN");
            return type.sql();
        }
        public void bindUuid(PreparedStatement ps,int position,java.util.UUID value)throws SQLException{
            // MySQL does not have a native UUID type; expect CHAR(36)/VARCHAR(36).
            ps.setString(position,value==null?null:value.toString());
        }
        public String upsertSql(String insertSql,String table,String idColumn,
                                List<String> columns,String versionColumn) {
            if(versionColumn!=null)
                throw new UnsupportedOperationException("Optimistic @Version upsert needs vendor-specific conflict handling; MySQL UPSERT is not enabled for versioned entities");
            if(columns.isEmpty())throw new UnsupportedOperationException("MySQL UPSERT requires a non-id update column");
            StringJoiner changes=new StringJoiner(", ");
            for(String column:columns)changes.add(column+" = bjorm_new."+column);
            // MySQL 8.0.19+ row alias; avoids deprecated VALUES(col).
            return insertSql+" AS bjorm_new ON DUPLICATE KEY UPDATE "+changes;
        }
    }
}
