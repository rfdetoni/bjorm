package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

/** Dialect SQL/binding boundaries; real database cases run separately in CI. */
public final class DialectSmokeTest {
    static void check(boolean valid,String text){if(!valid)throw new AssertionError(text);}
    static Object proxy(Class<?> type,InvocationHandler h){return Proxy.newProxyInstance(DialectSmokeTest.class.getClassLoader(),new Class<?>[]{type},h);}
    static final class Capture {
        String sql="";
        final Map<Integer,Object> binds=new TreeMap<>();
        DataSource source(){
            return (DataSource)proxy(DataSource.class,(o,m,a)->{
                if(!m.getName().equals("getConnection"))throw new UnsupportedOperationException(m.getName());
                return proxy(Connection.class,(c,cm,ca)->switch(cm.getName()){
                    case "prepareStatement" -> {
                        sql=(String)ca[0];binds.clear();
                        yield proxy(PreparedStatement.class,(ps,pm,pa)->switch(pm.getName()){
                            case "setObject","setString","setInt","setBigDecimal","setLong" -> {binds.put((Integer)pa[0],pa[1]);yield null;}
                            case "setQueryTimeout","setMaxRows","setFetchSize","close" -> null;
                            case "executeUpdate" -> 1;
                            case "executeQuery" -> proxy(ResultSet.class,(rs,rm,ra)->switch(rm.getName()){
                                case "next" -> false;
                                case "close" -> null;
                                default -> throw new UnsupportedOperationException(rm.getName());
                            });
                            default -> throw new UnsupportedOperationException(pm.getName());
                        });
                    }
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(cm.getName());
                });
            });
        }
    }
    public static void main(String[] ignored){
        Capture p=new Capture();
        Bjorm mysql=Bjorm.open(p.source(),BjormOptions.defaults(),SqlDialects.MYSQL,
            ItUser_BjormMapper.INSTANCE,ItDocument_BjormMapper.INSTANCE,ItProduct_BjormMapper.INSTANCE);
        UUID id=UUID.randomUUID();
        mysql.insertReturning(new ItUser(id,"Ana",15));
        check(p.sql.startsWith("INSERT INTO bjorm_it_users"),"MySQL insert SQL");
        check(id.toString().equals(p.binds.get(1)),"MySQL UUID should use CHAR(36) text");
        mysql.upsertReturning(new ItUser(id,"Bia",16));
        check(p.sql.contains(" AS bjorm_new ON DUPLICATE KEY UPDATE "),"MySQL native upsert alias");
        mysql.insertReturning(new ItDocument(null,"{\"ok\":true}"));
        check(p.sql.contains("CAST(? AS JSON)") && !p.sql.contains("jsonb"),"MySQL JSON dialect");
        try {
            mysql.select(ItUser.class).as("u").fullJoin(ItProduct.class,"p")
                .on(ItUser_.id.as("u").sameAs(ItProduct_.id.as("p"))).fetch();
            throw new AssertionError("FULL JOIN must fail on MySQL");
        } catch(UnsupportedOperationException expected) {
            check(expected.getMessage().contains("FULL OUTER"),"MySQL join error");
        }
        try {
            mysql.upsertReturning(new ItProduct(id,"x",java.math.BigDecimal.ONE,Status.ACTIVE,0));
            throw new AssertionError("Versioned MySQL upsert should fail safely");
        } catch(UnsupportedOperationException expected) {
            check(expected.getMessage().contains("@Version"),"MySQL versioned upsert rejection");
        }
        check(SqlDialects.named("mysql")==SqlDialects.MYSQL,"named mysql");
        check(SqlDialects.named("postgresql")==SqlDialects.POSTGRESQL,"named pg");
        System.out.println("PASS: SQL dialect abstraction, UUID binding, MySQL JSON/UPSERT, unsupported semantics");
    }
}
