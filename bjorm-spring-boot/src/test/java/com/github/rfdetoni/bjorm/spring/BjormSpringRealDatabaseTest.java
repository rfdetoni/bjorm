package com.github.rfdetoni.bjorm.spring;

import com.github.rfdetoni.bjorm.Bjorm;
import com.github.rfdetoni.bjorm.BjormOptions;
import com.github.rfdetoni.bjorm.EntityMapper;
import com.github.rfdetoni.bjorm.JdbcValues;
import com.github.rfdetoni.bjorm.SqlDialect;
import com.github.rfdetoni.bjorm.SqlDialects;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Real driver + Spring-bound transaction test, executed on disposable CI databases. */
class BjormSpringRealDatabaseTest {
    private record Row(UUID id,String label,int version) {}

    /** Test mapper uses the same compile-time-friendly JDBC contract as generated mappers. */
    private static final class RowMapper implements EntityMapper<Row> {
        @Override public Class<Row> type(){return Row.class;}
        @Override public String table(){return "bjorm_spring_rows";}
        @Override public String insertSql(){
            return "INSERT INTO bjorm_spring_rows (id,label,version) VALUES (?,?,?)";
        }
        @Override public String updateSql(){
            return "UPDATE bjorm_spring_rows SET label=?,version=version+1 WHERE id=? AND version=?";
        }
        @Override public String deleteSql(){
            return "DELETE FROM bjorm_spring_rows WHERE id=? AND version=?";
        }
        @Override public String selectSql(){
            return "SELECT id,label,version FROM bjorm_spring_rows WHERE id=?";
        }
        @Override public String selectAllSql(){
            return "SELECT id,label,version FROM bjorm_spring_rows";
        }
        @Override public void bindInsert(PreparedStatement ps,Row value)throws SQLException {
            bindInsert(ps,value,SqlDialects.POSTGRESQL);
        }
        @Override public void bindInsert(PreparedStatement ps,Row value,SqlDialect dialect)throws SQLException {
            dialect.bindValue(ps,1,value.id());
            ps.setString(2,value.label());
            ps.setInt(3,value.version());
        }
        @Override public void bindUpdate(PreparedStatement ps,Row value)throws SQLException {
            bindUpdate(ps,value,SqlDialects.POSTGRESQL);
        }
        @Override public void bindUpdate(PreparedStatement ps,Row value,SqlDialect dialect)throws SQLException {
            ps.setString(1,value.label());
            dialect.bindValue(ps,2,value.id());
            ps.setInt(3,value.version());
        }
        @Override public void bindId(PreparedStatement ps,int index,Object id)throws SQLException {
            ps.setObject(index,id);
        }
        @Override public Object id(Row row){return row.id();}
        @Override public Row read(ResultSet rs)throws SQLException {
            return new Row(JdbcValues.uuid(rs,1),rs.getString(2),rs.getInt(3));
        }
        @Override public boolean optimisticLocking(){return true;}
        @Override public String versionColumn(){return "version";}
        @Override public Object versionValue(Row row){return row.version();}
        @Override public String columnFor(String property){
            return switch(property){
                case "id","label","version" -> property;
                default -> throw new IllegalArgumentException("Unknown property");
            };
        }
    }

    @Test void joinsExistingSpringTransactionAndRollsBackWithRealDatabase()throws SQLException {
        String postgresUrl=System.getenv("BJORM_TEST_JDBC_URL");
        String mysqlUrl=System.getenv("BJORM_MYSQL_JDBC_URL");
        boolean postgres=postgresUrl!=null&&!postgresUrl.isBlank();
        boolean mysql=mysqlUrl!=null&&!mysqlUrl.isBlank();
        if(!postgres&&!mysql)return; // normal Java-only job needs no external DB
        String url=postgres?postgresUrl:mysqlUrl;
        String username=postgres
            ?System.getenv().getOrDefault("BJORM_TEST_USER","bjorm")
            :System.getenv().getOrDefault("BJORM_MYSQL_USER","bjorm");
        String password=postgres
            ?System.getenv().getOrDefault("BJORM_TEST_PASSWORD","bjorm_test")
            :System.getenv().getOrDefault("BJORM_MYSQL_PASSWORD","bjorm_test");
        String schema=postgres?"bjorm_spring_it":"bjorm_test";
        String table=schema+".bjorm_spring_rows";
        DriverManagerDataSource source=new DriverManagerDataSource(url,username,password);
        try(Connection connection=source.getConnection();Statement ddl=connection.createStatement()) {
            if(postgres)ddl.execute("CREATE SCHEMA IF NOT EXISTS bjorm_spring_it");
            ddl.execute("DROP TABLE IF EXISTS "+table);
            ddl.execute("CREATE TABLE "+table+" (id "+(postgres?"uuid":"char(36)")+
                " PRIMARY KEY,label varchar(255),version integer NOT NULL)");
        }
        try {
            DataSource springAware=new TransactionAwareDataSourceProxy(source);
            Bjorm root=Bjorm.tenantRequired(springAware,BjormOptions.defaults(),
                postgres?SqlDialects.POSTGRESQL:SqlDialects.MYSQL,new RowMapper());
            Bjorm tenant=root.forTenant(schema);
            TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            UUID id=UUID.randomUUID();
            Row current=tx.execute(status->{
                tenant.insertReturning(new Row(id,"created",0));
                Row updated=tenant.updateReturning(new Row(id,"updated",0));
                assertEquals(1,updated.version(),"internal UPDATE joined the Spring transaction");
                // Explicit db.tx must still reject nesting in Spring transactions.
                assertThrows(IllegalStateException.class,()->tenant.tx(inner->{}));
                return updated;
            });
            assertEquals(1,current.version());
            assertEquals(current,tenant.find(Row.class,id),"Spring committed exactly one version bump");

            UUID rolledBack=UUID.randomUUID();
            assertThrows(IllegalStateException.class,()->tx.execute(status->{
                tenant.insertReturning(new Row(rolledBack,"must rollback",0));
                tenant.updateReturning(new Row(id,"rollback-update",1));
                throw new IllegalStateException("Spring rollback test");
            }));
            assertNull(tenant.find(Row.class,rolledBack),"Spring rolled back inserted row");
            assertEquals(current,tenant.find(Row.class,id),"Spring rolled back version bump and label");
        } finally {
            try(Connection connection=source.getConnection();Statement ddl=connection.createStatement()){
                ddl.execute("DROP TABLE IF EXISTS "+table);
                if(postgres)ddl.execute("DROP SCHEMA IF EXISTS bjorm_spring_it");
            }
        }
    }
}
