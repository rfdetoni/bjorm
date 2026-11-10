package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.SqlDialects;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.UUID;

public final class MappingFlagsSmokeTest {
    static void check(boolean ok,String label) {if(!ok)throw new AssertionError(label);}
    public static void main(String[] args) throws Exception {
        var mapper=MappedColumns_BjormMapper.INSTANCE;
        String insert=mapper.insertSql(),update=mapper.updateSql(),select=mapper.selectAllSql();
        check(insert.contains("(id, read_only) VALUES (?, ?)"),"read-only column may be inserted");
        check(!insert.contains("server_val"),"database-generated column is excluded from insert");
        check(update.contains("server_val = ?") && !update.contains("read_only = ?"),
            "update uses only writable persisted column");
        check(select.contains("read_only, server_val") &&
              !select.contains("localCounter") && !select.contains("localHint"),
            "@Transient record state is omitted from SQL");
        try {mapper.bulkParameterSql("readOnly",SqlDialects.POSTGRESQL);
            throw new AssertionError("bulk mutation accepted read-only property");}
        catch(IllegalArgumentException expected) {}
        UUID id=UUID.randomUUID();
        Object[] cells={id,"read-only","server-generated"};
        ResultSet rs=(ResultSet)Proxy.newProxyInstance(MappingFlagsSmokeTest.class.getClassLoader(),
            new Class<?>[]{ResultSet.class},(obj,method,parameters)->switch(method.getName()) {
                case "getObject" -> cells[(Integer)parameters[0]-1];
                case "getString" -> cells[(Integer)parameters[0]-1];
                case "wasNull" -> false;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        MappedColumns read=mapper.read(rs);
        check(read.id().equals(id) && "read-only".equals(read.readOnly()) &&
              "server-generated".equals(read.serverValue()) &&
              read.localCounter()==0 && read.localHint()==null,
            "transient record constructor defaults and SQL position map");
        System.out.println("PASS: compile-time read-only/insertable flags, @Transient record defaults");
    }
}
