package com.github.rfdetoni.bjorm.spring;

import com.github.rfdetoni.bjorm.Bjorm;
import com.github.rfdetoni.bjorm.EntityMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BjormAutoConfigurationTest {
    @SuppressWarnings({"rawtypes","unchecked"})
    private static ObjectProvider<EntityMapper<?>> emptyMappers() {
        DefaultListableBeanFactory beanFactory=new DefaultListableBeanFactory();
        return (ObjectProvider)beanFactory.getBeanProvider(EntityMapper.class);
    }

    private static DataSource noDatabase(AtomicInteger connections) {
        return new AbstractDataSource() {
            @Override public Connection getConnection()throws SQLException {
                connections.incrementAndGet();
                throw new SQLException("No database is configured in this test");
            }
            @Override public Connection getConnection(String username,String password)throws SQLException {
                return getConnection();
            }
        };
    }

    @Test void tenantRequiredRootNeverBorrowsConnectionBeforeScoping() {
        AtomicInteger connections=new AtomicInteger();
        Bjorm root=new BjormAutoConfiguration().bjorm(
            noDatabase(connections),emptyMappers(),
            30,100_000,128,64,"postgresql",true);
        assertEquals(64,root.options().batchSize());
        assertEquals("postgresql",root.dialect().name());
        assertThrows(IllegalStateException.class,()->root.find(Object.class,123L));
        assertThrows(IllegalArgumentException.class,()->root.forTenant("public"));
        Bjorm scoped=root.forTenant("company_a");
        assertThrows(IllegalStateException.class,()->scoped.forTenant("company_b"));
        assertEquals(0,connections.get(),"fail closed before DataSource.getConnection");
    }

    @Test void legacyUnscopedModeRemainsDefaultCompatible() {
        AtomicInteger connections=new AtomicInteger();
        Bjorm root=new BjormAutoConfiguration().bjorm(
            noDatabase(connections),emptyMappers(),
            30,100_000,128,256,"mysql",false);
        assertEquals("mysql",root.dialect().name());
        assertEquals(256,root.options().batchSize());
        assertThrows(IllegalStateException.class,()->root.forTenant("company_a"),
            "tenant scoping must be explicitly enabled");
        assertEquals(0,connections.get());
    }
}
