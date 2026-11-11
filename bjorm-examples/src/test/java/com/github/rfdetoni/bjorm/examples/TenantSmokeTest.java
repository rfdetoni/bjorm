package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Bjorm;
import com.github.rfdetoni.bjorm.BjormOptions;
import com.github.rfdetoni.bjorm.SqlDialects;
import com.github.rfdetoni.bjorm.SqlSubqueries;
import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** No database needed: verifies fail-closed tenant access, transaction-local PG scope and MySQL reset. */
public final class TenantSmokeTest {
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type,InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    private static final class Spy {
        final List<String> events=new ArrayList<>();
        String catalog="root_catalog";
        boolean autoCommit=true;
        boolean failWrite;
        int connections;
        DataSource source() {
            return proxy(DataSource.class,(obj,method,args)->switch(method.getName()) {
                case "getConnection" -> {connections++;yield connection();}
                default -> throw new UnsupportedOperationException("DataSource."+method.getName());
            });
        }
        Connection connection() {
            return proxy(Connection.class,(obj,method,args)->switch(method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> {autoCommit=(boolean)args[0];events.add("auto="+autoCommit);yield null;}
                case "getCatalog" -> catalog;
                case "setCatalog" -> {catalog=(String)args[0];events.add("catalog="+catalog);yield null;}
                case "commit" -> {events.add("commit");yield null;}
                case "rollback" -> {events.add("rollback");yield null;}
                case "createStatement" -> statement();
                case "prepareStatement" -> prepared((String)args[0]);
                case "close" -> {events.add("close");yield null;}
                default -> throw new UnsupportedOperationException("Connection."+method.getName());
            });
        }
        Statement statement() {
            return proxy(Statement.class,(obj,method,args)->switch(method.getName()) {
                case "execute" -> {
                    check(!autoCommit,"SET LOCAL must run inside an active transaction");
                    events.add((String)args[0]);
                    yield true;
                }
                case "close" -> null;
                default -> throw new UnsupportedOperationException("Statement."+method.getName());
            });
        }
        PreparedStatement prepared(String sql) {
            return proxy(PreparedStatement.class,(obj,method,args)->switch(method.getName()) {
                case "executeUpdate" -> {
                    events.add("write:"+catalog+":"+sql);
                    if(failWrite)throw new SQLException("deliberate failure");
                    yield 1;
                }
                case "close","setQueryTimeout" -> null;
                default -> throw new UnsupportedOperationException("PreparedStatement."+method.getName());
            });
        }
    }
    public static void main(String[] args) {
        Spy pg=new Spy();
        Bjorm root=Bjorm.tenantRequired(pg.source(),BjormOptions.defaults(),SqlDialects.POSTGRESQL,ItUser_BjormMapper.INSTANCE);
        try {root.execute("UPDATE users SET name='x'",null);throw new AssertionError("unscoped root executed");}
        catch(IllegalStateException expected) {check(pg.connections==0,"must refuse before borrowing connection");}
        for(String unsafe:List.of("public","pg_catalog","pg_temp_1","information_schema","test; DROP TABLE users","UPPER.CASE")) {
            try {root.forTenant(unsafe);throw new AssertionError("unsafe schema accepted: "+unsafe);}
            catch(IllegalArgumentException expected) {}
        }
        Bjorm mixed=Bjorm.tenantRequired(pg.source(),BjormOptions.defaults(),
            SqlDialects.POSTGRESQL,StaticSchemaRecord_BjormMapper.INSTANCE);
        int connectionBefore=pg.connections;
        try {mixed.forTenant("company_a");
            throw new AssertionError("fixed-schema mapper accepted in tenant-required context");}
        catch(IllegalStateException expected) {
            check(expected.getMessage().contains("Fixed-schema"),"static mapping must be explicitly refused");
        }
        check(pg.connections==connectionBefore,"fixed schema is rejected before connection checkout");
        Bjorm guarded=root.forTenant("company_a");
        try {guarded.forTenant("company_b");throw new AssertionError("tenant rebind allowed");}
        catch(IllegalStateException expected) {}
        int borrowed=pg.connections;
        try {guarded.execute("UPDATE company_b.users SET name='x'",ps->{});
            throw new AssertionError("Native SQL bypassed tenant permissions");}
        catch(SecurityException expected) {}
        check(pg.connections==borrowed,"native SQL denied before connection checkout");
        int beforeLock=pg.events.size();
        try {
            guarded.lockOne(ItUser.class,ItUser_.name.eq("x"),
                com.github.rfdetoni.bjorm.LockMode.UPDATE);
            throw new AssertionError("PG tenant lock acquired inside an implicit auto transaction");
        } catch(IllegalStateException expected) {}
        try {
            guarded.lockRows(ItUser.class,ItUser_.name.eq("x"),
                com.github.rfdetoni.bjorm.LockMode.UPDATE_SKIP_LOCKED,1);
            throw new AssertionError("PG tenant lockRows acquired without explicit transaction");
        } catch(IllegalStateException expected) {}
        check(pg.events.subList(beforeLock,pg.events.size()).stream().allMatch("close"::equals) && pg.autoCommit,
            "PG tenant row locks must reject auto-commit without SET LOCAL or implicit commit");
        Bjorm scoped=guarded.permitReviewedTenantNativeSql();
        check(scoped.execute("UPDATE users SET name='x'",ps->{})==1,"reviewed tenant-scoped update");
        check(pg.events.contains("SET LOCAL search_path TO pg_catalog, \"company_a\", pg_temp"),"scoped PostgreSQL search_path");
        check(pg.events.contains("commit") && pg.autoCommit,"PG auto-transaction restores auto-commit");
        int writes=pg.events.size();
        pg.failWrite=true;
        try {scoped.execute("UPDATE users SET name='x'",ps->{});throw new AssertionError("failure not propagated");}
        catch(RuntimeException expected) {}
        check(pg.events.subList(writes,pg.events.size()).contains("rollback"),"PG scoped JDBC failure rolls back");
        pg.failWrite=false;
        scoped.tx(tx->check(tx.execute("UPDATE users SET name='x'",ps->{})==1,"PG explicit transaction"));
        check(pg.events.stream().filter(x->x.startsWith("SET LOCAL search_path")).count()==3,
            "every borrowed PG transaction is explicitly tenant-scoped");
        Spy mysql=new Spy();
        Bjorm mysqlRoot=Bjorm.tenantRequired(mysql.source(),BjormOptions.defaults(),SqlDialects.MYSQL,ItUser_BjormMapper.INSTANCE);
        Bjorm mysqlGuarded=mysqlRoot.forTenant("company_a");
        int mysqlBorrowed=mysql.connections;
        try {mysqlGuarded.execute("UPDATE company_b.users SET name='y'",ps->{});
            throw new AssertionError("MySQL tenant accepted raw SQL");}
        catch(SecurityException expected) {}
        check(mysql.connections==mysqlBorrowed,"MySQL native SQL denied before JDBC checkout");
        int mysqlLockEvents=mysql.events.size();
        try {
            mysqlGuarded.lockOne(ItUser.class,ItUser_.name.eq("x"),
                com.github.rfdetoni.bjorm.LockMode.UPDATE);
            throw new AssertionError("MySQL tenant lock acquired without explicit transaction");
        } catch(IllegalStateException expected) {}
        check(mysql.events.subList(mysqlLockEvents,mysql.events.size()).stream().allMatch("close"::equals) && mysql.catalog.equals("root_catalog"),
            "MySQL rejected row lock cannot alter tenant catalog");
        Bjorm tenant=mysqlGuarded.permitReviewedTenantNativeSql();
        check(tenant.execute("UPDATE users SET name='y'",ps->{})==1,"reviewed MySQL tenant write");
        check(mysql.events.contains("write:company_a:UPDATE users SET name='y'"),"MySQL catalog scoped on write");
        check(mysql.catalog.equals("root_catalog"),"MySQL catalog restored");
        mysql.failWrite=true;
        try {tenant.execute("UPDATE users SET name='z'",ps->{});throw new AssertionError("failed MySQL write accepted");}
        catch(RuntimeException expected) {}
        check(mysql.catalog.equals("root_catalog"),"MySQL catalog restored after failure");
        mysql.failWrite=false;
        tenant.tx(tx->check(tx.execute("UPDATE users SET name='tx'",ps->{})==1,"MySQL transactional catalog"));
        check(mysql.catalog.equals("root_catalog"),"MySQL catalog restored after transaction");
        // An unqualified table and validated alias are mandatory even for a nested
        // subquery; otherwise generated DSL could bypass tenant-scoped search_path.
        try {
            SqlSubqueries.exists(StaticSchemaRecord_BjormMapper.INSTANCE,"s",
                ItUser_.name.eq("bad"));
            throw new AssertionError("Static cross-schema subquery was accepted");
        } catch(IllegalArgumentException expected) {}
        try {
            SqlSubqueries.exists(ItInvoice_BjormMapper.INSTANCE,"i;DROP TABLE users",
                ItUser_.name.eq("bad"));
            throw new AssertionError("Unsafe subquery alias was accepted");
        } catch(IllegalArgumentException expected) {}
        try {
            SqlSubqueries.in(ItUser_.id,ItInvoice_BjormMapper.INSTANCE,"i",
                ItInvoice_.customerId.as("other"),ItInvoice_.reference.as("i").eq("bad"));
            throw new AssertionError("A projection from a foreign subquery alias was accepted");
        } catch(IllegalArgumentException expected) {}
        Spy isolatedA=new Spy(),isolatedB=new Spy();
        Bjorm isolatedRoot=Bjorm.tenantRequired(
            java.util.Map.of("company_a",isolatedA.source(),"company_b",isolatedB.source()),
            BjormOptions.defaults(),SqlDialects.MYSQL,ItUser_BjormMapper.INSTANCE);
        try {isolatedRoot.find(ItUser.class,java.util.UUID.randomUUID());
            throw new AssertionError("Tenant registry root borrowed an unscoped connection");}
        catch(IllegalStateException expected) {}
        check(isolatedA.connections==0 && isolatedB.connections==0,
            "Tenant registry root has no fallback JDBC connection");
        try {isolatedRoot.forTenant("unknown_company");
            throw new AssertionError("Unknown tenant selected a JDBC DataSource");}
        catch(SecurityException expected) {}
        check(isolatedA.connections==0 && isolatedB.connections==0,
            "Unknown tenant is denied before opening any credentialed connection");
        Bjorm selectedA=isolatedRoot.forTenant("company_a").permitReviewedTenantNativeSql();
        check(selectedA.execute("UPDATE users SET name='registry-a'",ps->{})==1,
            "Tenant A uses registered least-privilege DataSource");
        check(isolatedA.events.stream().anyMatch(e->e.contains("write:company_a:")) &&
            isolatedB.connections==0,"Tenant A cannot borrow tenant B credentials");
        Bjorm selectedB=isolatedRoot.observeSql((operation,nanos,ok)->{})
            .forTenant("company_b").permitReviewedTenantNativeSql();
        check(selectedB.execute("UPDATE users SET name='registry-b'",ps->{})==1,
            "Tenant B is bound to its own immutable DataSource");
        check(isolatedB.events.stream().anyMatch(e->e.contains("write:company_b:")),
            "Tenant B connection cannot reuse tenant A credentials");
        try {selectedA.forTenant("company_b");throw new AssertionError("Scoped tenant switched pool");}
        catch(IllegalStateException expected) {}
        try {Bjorm.tenantRequired(java.util.Map.of("public",isolatedA.source()),
            BjormOptions.defaults(),SqlDialects.MYSQL,ItUser_BjormMapper.INSTANCE);
            throw new AssertionError("Unsafe tenant registry key accepted");}
        catch(IllegalArgumentException expected) {}
        Spy preflight=new Spy();
        Bjorm detachedDb=Bjorm.open(preflight.source(),ItOrder_BjormMapper.INSTANCE,
            ItOrderLine_BjormMapper.INSTANCE,ItOrderNote_BjormMapper.INSTANCE);
        ItOrder duplicateRoot=new ItOrder();
        duplicateRoot.setDescription("unsafe shared child");
        ItOrderLine sharedChild=new ItOrderLine();
        sharedChild.setSku("SHARED");
        duplicateRoot.getLines().add(sharedChild);
        duplicateRoot.getLines().add(sharedChild);
        try {detachedDb.insertDetached(duplicateRoot);
            throw new AssertionError("Repeated detached child was accepted");}
        catch(IllegalArgumentException expected) {}
        check(preflight.connections==0 && duplicateRoot.getId()==null &&
            sharedChild.getId()==null && sharedChild.getOrderId()==null,
            "Detached graph validation must reject repeated objects before JDBC or mutation");
        System.out.println("PASS: tenant required, scoped PG SET LOCAL, rollback and MySQL catalog reset");
    }
}
