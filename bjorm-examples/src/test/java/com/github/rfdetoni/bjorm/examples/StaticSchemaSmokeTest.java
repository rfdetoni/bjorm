package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.SqlDialects;

public final class StaticSchemaSmokeTest {
    private static void check(boolean ok,String description){if(!ok)throw new AssertionError(description);}
    public static void main(String[] args) {
        var m=StaticSchemaRecord_BjormMapper.INSTANCE;
        check("bjorm_test".equals(m.staticSchema()),"fixed schema metadata");
        check("bjorm_test.bjorm_it_static".equals(m.table()),"qualified table identifier");
        check(m.insertSql().startsWith("INSERT INTO bjorm_test.bjorm_it_static ("),
            "SQL INSERT is schema-qualified");
        check(m.selectAllSql().contains("FROM bjorm_test.bjorm_it_static"),"SELECT target qualified");
        check(m.updateSql().contains("UPDATE bjorm_test.bjorm_it_static SET"),"UPDATE target qualified");
        String pg=m.upsertSql(SqlDialects.POSTGRESQL);
        check(pg.contains("ON CONFLICT (id) DO UPDATE SET"),"PostgreSQL schema-qualified upsert");
        String mysql=m.upsertSql(SqlDialects.MYSQL);
        check(mysql.contains(" AS bjorm_new ON DUPLICATE KEY UPDATE "),
            "MySQL schema-qualified upsert");
        check(!mysql.contains("bjorm_test.bjorm_it_static.id = IF"),
            "MySQL conflict predicate refers to the target table");
        System.out.println("PASS: fixed schema metadata and PostgreSQL/MySQL SQL generation");
    }
}
