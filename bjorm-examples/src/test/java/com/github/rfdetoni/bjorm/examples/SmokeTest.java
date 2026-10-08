package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.*;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** No external test framework; smoke checks JDBC binding, transaction ownership, rollback and generated mapper. */
public final class SmokeTest {
    private record Executed(int connection, String sql, Map<Integer, Object> params) {}
    private static final class FakeJdbc implements InvocationHandler {
        private final int id;
        private final List<Executed> statements;
        private boolean autoCommit = true;
        private final AtomicInteger commits;
        private final AtomicInteger rollbacks;
        FakeJdbc(int id,List<Executed> statements,AtomicInteger commits,AtomicInteger rollbacks) {
            this.id=id;this.statements=statements;this.commits=commits;this.rollbacks=rollbacks;
        }
        Connection connection() { return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},this); }
        @Override public Object invoke(Object p,Method m,Object[] a) {
            return switch (m.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> {autoCommit=(boolean)a[0];yield null;}
                case "commit" -> {commits.incrementAndGet();yield null;}
                case "rollback" -> {rollbacks.incrementAndGet();yield null;}
                case "prepareStatement" -> prepared((String)a[0]);
                case "close" -> null;
                case "isClosed" -> false;
                case "toString" -> "FakeConnection "+id;
                default -> throw new UnsupportedOperationException("Connection."+m.getName());
            };
        }
        PreparedStatement prepared(String sql) {
            Map<Integer,Object> params = new TreeMap<>();
            InvocationHandler ps = (p,m,a) -> switch(m.getName()) {
                case "setString", "setObject", "setInt", "setLong", "setBoolean" -> {params.put((Integer)a[0],a[1]);yield null;}
                case "executeUpdate" -> {statements.add(new Executed(id,sql,Map.copyOf(params)));yield 1;}
                case "executeQuery" -> {
                    statements.add(new Executed(id,sql,Map.copyOf(params)));
                    yield Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(pr,mr,ar)-> switch(mr.getName()) {
                        case "next" -> false;
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException("ResultSet."+mr.getName());
                    });
                }
                case "close","setQueryTimeout","setMaxRows" -> null;
                default -> throw new UnsupportedOperationException("PreparedStatement."+m.getName());
            };
            return (PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},ps);
        }
    }
    private static void expect(boolean b,String reason) {if(!b)throw new AssertionError(reason);}
    public static void main(String[] args) {
        var connections = new AtomicInteger(); var commits = new AtomicInteger(); var rollbacks = new AtomicInteger();
        List<Executed> statements = new ArrayList<>();
        InvocationHandler ds = (p,m,a)-> switch(m.getName()) {
            case "getConnection" -> new FakeJdbc(connections.incrementAndGet(),statements,commits,rollbacks).connection();
            default -> throw new UnsupportedOperationException("DataSource."+m.getName());
        };
        DataSource datasource = (DataSource)Proxy.newProxyInstance(SmokeTest.class.getClassLoader(),new Class[]{DataSource.class},ds);
        var db = Bjorm.open(datasource,User_BjormMapper.INSTANCE);
        User u = new User(UUID.randomUUID(),"Ana",28);
        db.insert(u);
        expect(statements.getLast().sql().equals("INSERT INTO users (id, name, age) VALUES (?, ?, ?)"),"compiled INSERT");
        expect(statements.getLast().params().equals(Map.of(1,u.id(),2,u.name(),3,u.age())),"bind order");
        db.list(User.class,User_.name.eq("test' OR 1=1 --").and(User_.age.gt(18)));
        var listed = statements.getLast();
        expect(listed.sql().equals("SELECT id, name, age FROM users WHERE (name = ? AND age > ?)"),"DSL SQL shape");
        expect(listed.params().get(1).equals("test' OR 1=1 --"),"SQL value parameterization");
        int before = connections.get();
        db.tx(tx->{tx.update(u);tx.delete(u);});
        expect(connections.get()==before+1,"transaction borrows one connection");
        var two = statements.subList(statements.size()-2,statements.size());
        expect(two.get(0).connection()==two.get(1).connection(),"transaction shares same connection");
        expect(two.get(0).params().equals(Map.of(1, u.name(), 2, u.age(), 3, u.id())), "UPDATE binding order");
        expect(commits.get()==1&&rollbacks.get()==0,"transaction commits");
        try {db.tx(tx->{tx.insert(u);throw new IllegalStateException("fail");});throw new AssertionError("should fail");}
        catch(IllegalStateException expected) {expect(expected.getMessage().equals("fail"),"original exception propagated");}
        expect(rollbacks.get()==1,"transaction rolls back");
        expect(User_.name.eq(null).sql().equals("name IS NULL"),"NULL comparison");
        var escapedTx = new AtomicReference<Bjorm.Transaction>();
        db.tx(escapedTx::set);
        try {escapedTx.get().delete(u);throw new AssertionError("escaped transaction should be closed");}
        catch(IllegalStateException expected) {expect(expected.getMessage().contains("no longer active"),"closed transaction guard");}
        try {
            ResultSet nullPrimitive = (ResultSet) Proxy.newProxyInstance(SmokeTest.class.getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->switch(m.getName()) {
                case "getInt" -> 0;
                case "wasNull" -> true;
                default -> throw new UnsupportedOperationException(m.getName());
            });
            try {JdbcValues.requiredInt(nullPrimitive,3);throw new AssertionError("null should fail");}
            catch(SQLException expected) {expect(expected.getMessage().contains("NULL"),"NULL primitive guard");}
        } catch(Exception e) {throw new AssertionError(e);}

        UUID last = UuidV7.next();
        expect(last.version() == 7 && last.variant() == 2, "UUID v7 version and RFC variant");
        for (int i = 0; i < 2000; i++) {
            UUID current = UuidV7.next();
            expect(current.version() == 7 && current.variant() == 2, "UUID v7 bits");
            expect(last.compareTo(current) < 0, "UUID v7 monotonic order");
            last = current;
        }
        System.out.println("PASS: generated SQL, DSL, transactions, primitive NULL guard and monotonic UUID v7");
    }
}
