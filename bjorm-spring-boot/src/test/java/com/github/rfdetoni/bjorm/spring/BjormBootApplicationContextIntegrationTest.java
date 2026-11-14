package com.github.rfdetoni.bjorm.spring;

import com.github.rfdetoni.bjorm.Bjorm;
import com.github.rfdetoni.bjorm.EntityMapper;
import com.github.rfdetoni.bjorm.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boots the real BJORM auto-configuration with a real driver, not a direct
 * factory call. The generated JDBC mapper bean participates in Spring-owned
 * transaction boundaries and is still subject to fail-closed tenant checks.
 */
class BjormBootApplicationContextIntegrationTest {
    @Test void autoConfigurationWithLivePostgresOrMySql()throws SQLException {
        String pgUrl=System.getenv("BJORM_TEST_JDBC_URL");
        String mysqlUrl=System.getenv("BJORM_MYSQL_JDBC_URL");
        boolean pg=pgUrl!=null&&!pgUrl.isBlank();
        boolean mysql=mysqlUrl!=null&&!mysqlUrl.isBlank();
        if(!pg&&!mysql)return; // Java-only verification job has no database
        String url=pg?pgUrl:mysqlUrl;
        String user=pg?System.getenv().getOrDefault("BJORM_TEST_USER","bjorm")
                      :System.getenv().getOrDefault("BJORM_MYSQL_USER","bjorm");
        String password=pg?System.getenv().getOrDefault("BJORM_TEST_PASSWORD","bjorm_test")
                          :System.getenv().getOrDefault("BJORM_MYSQL_PASSWORD","bjorm_test");
        String tenantA=pg?"bjorm_boot_a":"bjorm_test";
        String tenantB=pg?"bjorm_boot_b":null;
        DriverManagerDataSource source=new DriverManagerDataSource(url,user,password);
        try(Connection conn=source.getConnection();Statement st=conn.createStatement()) {
            if(pg) {
                st.execute("CREATE SCHEMA IF NOT EXISTS bjorm_boot_a");
                st.execute("CREATE SCHEMA IF NOT EXISTS bjorm_boot_b");
            }
            st.execute("CREATE TABLE "+tenantA+".bjorm_boot_rows (id "+
                (pg?"uuid":"char(36)")+" PRIMARY KEY,label varchar(255),version integer NOT NULL)");
            if(pg)st.execute("CREATE TABLE bjorm_boot_b.bjorm_boot_rows (id uuid PRIMARY KEY,label varchar(255),version integer NOT NULL)");
        }
        try {
            new ApplicationContextRunner()
                .withBean(DataSource.class,()->source)
                .withBean(EntityMapper.class,
                    ()->new BjormSpringRealDatabaseTest.RowMapper("bjorm_boot_rows"))
                .withConfiguration(AutoConfigurations.of(BjormAutoConfiguration.class))
                .withPropertyValues(
                    "bjorm.tenant.required=true",
                    "bjorm.dialect="+(pg?"postgresql":"mysql"),
                    "bjorm.jdbc.fetch-size=16",
                    "bjorm.jdbc.batch-size=8",
                    "bjorm.pagination.max-page-size=7")
                .run(context->{
                    assertNull(context.getStartupFailure(),"Boot auto-configuration failed to start");
                    assertNotNull(context.getBean(BjormPages.class),"Boot provided paging bean");
                    assertEquals(7, context.getBean(BjormPages.class).maxPageSize(),
                        "Boot must honor its max-page-size resource limit");
                    Bjorm root=context.getBean(Bjorm.class);
                    assertThrows(IllegalStateException.class,
                        ()->root.find(BjormSpringRealDatabaseTest.Row.class,UUID.randomUUID()),
                        "Boot tenant-required property must fail closed");
                    Bjorm a=root.forTenant(tenantA);
                    TransactionTemplate tx=new TransactionTemplate(
                        new DataSourceTransactionManager(source));
                    UUID id=UUID.randomUUID();
                    BjormSpringRealDatabaseTest.Row committed=tx.execute(status->{
                        a.insertReturning(new BjormSpringRealDatabaseTest.Row(id,"created",0));
                        var updated=a.updateReturning(
                            new BjormSpringRealDatabaseTest.Row(id,"committed",0));
                        assertEquals(1,updated.version());
                        assertThrows(IllegalStateException.class,()->a.tx(inner->{}),
                            "Explicit BJORM tx must not nest inside Spring");
                        return updated;
                    });
                    assertEquals(committed,a.find(BjormSpringRealDatabaseTest.Row.class,id));
                    UUID rollbackId=UUID.randomUUID();
                    assertThrows(IllegalStateException.class,()->tx.execute(status->{
                        a.insertReturning(new BjormSpringRealDatabaseTest.Row(
                            rollbackId,"rollback",0));
                        a.updateReturning(new BjormSpringRealDatabaseTest.Row(
                            id,"not-committed",1));
                        throw new IllegalStateException("trigger rollback");
                    }));
                    assertNull(a.find(BjormSpringRealDatabaseTest.Row.class,rollbackId));
                    assertEquals(committed,a.find(BjormSpringRealDatabaseTest.Row.class,id),
                        "Spring manager controls rollback of all joined operations");
                    // Without an ID tie-breaker, offset pages with duplicate labels can
                    // repeat or skip rows. Verify both SQL execution and deterministic order.
                    UUID second=UUID.randomUUID(), third=UUID.randomUUID();
                    a.insertReturning(new BjormSpringRealDatabaseTest.Row(second,"same",0));
                    a.insertReturning(new BjormSpringRealDatabaseTest.Row(third,"same",0));
                    BjormPages pages=new BjormPages(a,10_000,7);
                    var byLabel=Sort.by("label");
                    var page0=pages.slice(BjormSpringRealDatabaseTest.Row.class,PageRequest.of(0,1,byLabel));
                    var page1=pages.slice(BjormSpringRealDatabaseTest.Row.class,PageRequest.of(1,1,byLabel));
                    var page2=pages.slice(BjormSpringRealDatabaseTest.Row.class,PageRequest.of(2,1,byLabel));
                    assertTrue(page0.hasNext() && page1.hasNext() && !page2.hasNext(),
                        "slice lookahead and stable tie-breaking on the generated primary key");
                    assertEquals(3,java.util.Set.of(page0.getContent().getFirst().id(),
                        page1.getContent().getFirst().id(),page2.getContent().getFirst().id()).size(),
                        "multiple equal labels must not repeat rows across pages");
                    assertEquals(page1.getContent(),
                        pages.slice(BjormSpringRealDatabaseTest.Row.class,
                            PageRequest.of(1,1,byLabel)).getContent(),
                        "same request yields the same ordered slice without writes");
                    assertThrows(IllegalArgumentException.class,
                        ()->pages.slice(BjormSpringRealDatabaseTest.Row.class,
                            PageRequest.of(0,8,byLabel)),
                        "configured maxPageSize applies before SQL");
                    // Keyset: no COUNT/OFFSET; sorted value and ID both advance.
                    var seek0=pages.seekSliceSorted(BjormSpringRealDatabaseTest.Row.class,
                        "label",null,1);
                    assertTrue(seek0.hasNext(),"first sorted cursor page has next");
                    var cursor0=(SortedCursor)seek0.nextCursor();
                    assertEquals(seek0.content().getFirst().label(),cursor0.sortValue());
                    var seek1=pages.seekSliceSorted(BjormSpringRealDatabaseTest.Row.class,
                        "label",cursor0,1);
                    var seek2=pages.seekSliceSorted(BjormSpringRealDatabaseTest.Row.class,
                        "label",(SortedCursor)seek1.nextCursor(),1);
                    assertTrue(seek1.hasNext() && !seek2.hasNext(),
                        "cursor must advance past duplicate sort values");
                    assertEquals(3,java.util.Set.of(seek0.content().getFirst().id(),
                        seek1.content().getFirst().id(),seek2.content().getFirst().id()).size(),
                        "composite cursor never duplicates rows sharing the same label");
                    assertThrows(IllegalArgumentException.class,
                        ()->pages.seekSliceSorted(BjormSpringRealDatabaseTest.Row.class,
                            "noSuchColumn",null,1),
                        "keyset sort field must come from the generated mapper");

                    if(pg) {
                        Bjorm b=root.forTenant(tenantB);
                        b.insertReturning(new BjormSpringRealDatabaseTest.Row(id,"tenant-b",0));
                        assertEquals("tenant-b",
                            b.find(BjormSpringRealDatabaseTest.Row.class,id).label());
                        assertEquals("committed",
                            a.find(BjormSpringRealDatabaseTest.Row.class,id).label(),
                            "Postgres tenant schemas must remain isolated by Boot context");
                    }
                    assertThrows(SecurityException.class,
                        ()->a.execute("UPDATE "+tenantA+".bjorm_boot_rows SET label='x'",
                            ps->{}),"Native SQL cannot bypass tenant isolation in Boot context");
                });
        } finally {
            try(Connection conn=source.getConnection();Statement st=conn.createStatement()) {
                st.execute("DROP TABLE IF EXISTS "+tenantA+".bjorm_boot_rows");
                if(pg) {
                    st.execute("DROP TABLE IF EXISTS bjorm_boot_b.bjorm_boot_rows");
                    st.execute("DROP SCHEMA IF EXISTS bjorm_boot_a");
                    st.execute("DROP SCHEMA IF EXISTS bjorm_boot_b");
                }
            }
        }
    }
}
