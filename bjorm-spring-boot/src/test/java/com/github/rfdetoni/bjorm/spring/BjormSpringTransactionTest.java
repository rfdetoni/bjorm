package com.github.rfdetoni.bjorm.spring;

import com.github.rfdetoni.bjorm.Bjorm;
import com.github.rfdetoni.bjorm.BjormOptions;
import com.github.rfdetoni.bjorm.SqlDialects;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A transaction-manager test that confirms BJORM uses the Spring-bound physical JDBC connection. */
class BjormSpringTransactionTest {
    static final class Spy {
        final AtomicInteger borrowed=new AtomicInteger();
        final AtomicInteger commits=new AtomicInteger();
        final AtomicInteger rollbacks=new AtomicInteger();
        final AtomicInteger closed=new AtomicInteger();
        final AtomicInteger searches=new AtomicInteger();
        final AtomicInteger writes=new AtomicInteger();
        final AtomicBoolean autoCommit=new AtomicBoolean(true);
        final Connection connection=(Connection)Proxy.newProxyInstance(
            BjormSpringTransactionTest.class.getClassLoader(),new Class<?>[]{Connection.class},
            (object,method,args)->switch(method.getName()) {
                case "getAutoCommit" -> autoCommit.get();
                case "setAutoCommit" -> {autoCommit.set((boolean)args[0]);yield null;}
                case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                case "setTransactionIsolation","setReadOnly" -> null;
                case "isReadOnly" -> false;
                case "commit" -> {commits.incrementAndGet();yield null;}
                case "rollback" -> {rollbacks.incrementAndGet();yield null;}
                case "close" -> {closed.incrementAndGet();yield null;}
                case "isClosed" -> false;
                case "createStatement" -> statement();
                case "prepareStatement" -> prepared();
                case "toString" -> "TestConnection";
                case "hashCode" -> System.identityHashCode(object);
                case "equals" -> object==args[0];
                default -> throw new UnsupportedOperationException("Connection."+method.getName());
            });
        DataSource dataSource() {
            return new AbstractDataSource() {
                @Override public Connection getConnection()throws SQLException {
                    borrowed.incrementAndGet();
                    return connection;
                }
                @Override public Connection getConnection(String username,String password)throws SQLException {
                    return getConnection();
                }
            };
        }
        Statement statement() {
            return (Statement)Proxy.newProxyInstance(
                BjormSpringTransactionTest.class.getClassLoader(),new Class<?>[]{Statement.class},
                (object,method,args)->switch(method.getName()) {
                    case "execute" -> {
                        assertTrue(!autoCommit.get(),"SET LOCAL requires active Spring transaction");
                        assertTrue(((String)args[0]).contains("search_path"));
                        searches.incrementAndGet();
                        yield true;
                    }
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException("Statement."+method.getName());
                });
        }
        PreparedStatement prepared() {
            return (PreparedStatement)Proxy.newProxyInstance(
                BjormSpringTransactionTest.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},
                (object,method,args)->switch(method.getName()) {
                    case "setString","setQueryTimeout","close" -> null;
                    case "executeUpdate" -> {writes.incrementAndGet();yield 1;}
                    default -> throw new UnsupportedOperationException("PreparedStatement."+method.getName());
                });
        }
    }

    @Test void mappedAndNativeJDBCReuseSpringBoundConnection() {
        Spy spy=new Spy();
        DataSource raw=spy.dataSource();
        DataSourceTransactionManager manager=new DataSourceTransactionManager(raw);
        TransactionTemplate template=new TransactionTemplate(manager);
        Bjorm root=Bjorm.tenantRequired(new TransactionAwareDataSourceProxy(raw),
            BjormOptions.defaults(),SqlDialects.POSTGRESQL);
        Bjorm tenant=root.forTenant("company_a").permitReviewedTenantNativeSql();

        template.execute(status -> {
            assertEquals(1,tenant.execute("UPDATE items SET label=?",ps->ps.setString(1,"one")));
            assertEquals(1,tenant.execute("UPDATE items SET label=?",ps->ps.setString(1,"two")));
            assertEquals(1,spy.borrowed.get(),"Spring-bound physical connection reused");
            assertEquals(0,spy.commits.get(),"BJORM must not commit Spring transaction");
            assertThrows(IllegalStateException.class,()->tenant.tx(tx->{}),
                "Explicit BJORM transaction inside Spring must fail rather than nest");
            return null;
        });
        assertEquals(1,spy.commits.get(),"Spring manager commits exactly once");
        assertEquals(2,spy.searches.get(),"Every statement is scoped to the authorized schema");
        assertEquals(2,spy.writes.get());
        assertTrue(spy.autoCommit.get(),"Spring manager restores JDBC auto-commit");

        RuntimeException failure=assertThrows(RuntimeException.class,()->template.execute(status -> {
            tenant.execute("UPDATE items SET label=?",ps->ps.setString(1,"rollback"));
            throw new RuntimeException("force rollback");
        }));
        assertEquals("force rollback",failure.getMessage());
        assertEquals(1,spy.rollbacks.get(),"Spring controls rollback and BJORM does not override it");
        assertEquals(1,spy.commits.get(),"No commit on failed Spring transaction");
        assertEquals(3,spy.searches.get(),"Tenant context applied to rollback transaction");
    }
}
