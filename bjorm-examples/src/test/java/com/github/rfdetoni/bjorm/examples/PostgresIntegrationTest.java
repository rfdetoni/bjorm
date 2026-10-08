package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.*;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

/** Opt-in end-to-end JDBC integration. Requires a local PostgreSQL database and driver on the classpath. */
public final class PostgresIntegrationTest {
    private static void check(boolean condition,String description){if(!condition)throw new AssertionError(description);}
    public static void main(String[] ignored)throws Exception {
        String url=System.getenv("BJORM_TEST_JDBC_URL");
        if(url==null||url.isBlank()){System.out.println("SKIP: BJORM_TEST_JDBC_URL not configured");return;}
        String user=System.getenv().getOrDefault("BJORM_TEST_USER","");
        String password=System.getenv().getOrDefault("BJORM_TEST_PASSWORD","");
        DataSource ds=new DataSource(){
            public Connection getConnection()throws SQLException{return DriverManager.getConnection(url,user,password);}
            public Connection getConnection(String u,String p)throws SQLException{return DriverManager.getConnection(url,u,p);}
            public PrintWriter getLogWriter(){return null;}
            public void setLogWriter(PrintWriter w){}
            public void setLoginTimeout(int t){}
            public int getLoginTimeout(){return 0;}
            public Logger getParentLogger(){return Logger.getGlobal();}
            public <T>T unwrap(Class<T> c)throws SQLException{throw new SQLException("unsupported");}
            public boolean isWrapperFor(Class<?> c){return false;}
        };
        // Use an expendable test database. Never run against an existing schema with fixture table names.
        try(Connection c=ds.getConnection();Statement st=c.createStatement()){
            st.execute("CREATE TABLE bjorm_it_users (id uuid PRIMARY KEY, name text NOT NULL, age integer NOT NULL)");
            st.execute("CREATE TABLE bjorm_it_products (id uuid PRIMARY KEY, name text, unit_price numeric(18,2), status text, version integer NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE bjorm_it_documents (id uuid PRIMARY KEY, payload jsonb)");
        }
        Bjorm db=Bjorm.open(ds,ItUser_BjormMapper.INSTANCE,ItProduct_BjormMapper.INSTANCE,ItDocument_BjormMapper.INSTANCE);
        UUID id=UUID.randomUUID();ItUser alice=new ItUser(id,"BJORM",34);
        try {
            db.insert(alice);
            check(db.find(ItUser.class,id).equals(alice),"actual database CRUD");
            check(db.list(ItUser.class,ItUser_.name.eq("BJORM")).stream().anyMatch(x->x.id().equals(id)),"DSL binding against PostgreSQL");
            check(db.findOne(ItUser.class,ItUser_.name.eq("BJORM")).orElseThrow().equals(alice),"findOne Optional against PostgreSQL");
            check(db.findOne(ItUser.class,ItUser_.name.eq("missing")).isEmpty(),"findOne missing record");
            var fields=db.findFields(ItUser.class,ItUser_.name.eq("BJORM"),"name","age");
            check(fields.size()==1 && fields.getFirst().get("age").equals(34),"named Java properties return only selected columns");
            check(db.findColumnOne(ItUser.class,"name",String.class,ItUser_.age.eq(34)).orElseThrow().equals("BJORM"),"typed scalar projection");

            try{db.tx(tx->{tx.insert(new ItUser(UUID.randomUUID(),"SHOULD_ROLLBACK",11));throw new IllegalStateException("rollback");});}catch(IllegalStateException expected){}
            check(db.list(ItUser.class,ItUser_.name.eq("SHOULD_ROLLBACK")).isEmpty(),"real transaction rollback");
            ItProduct product=new ItProduct(id,"Pencil",new BigDecimal("4.50"),Status.ACTIVE,0);
            db.insert(product);
            var selectedPrice=db.findOneFields(ItProduct.class,ItProduct_.name.eq("Pencil"),"price").orElseThrow();
            check(selectedPrice.get("price").equals(new BigDecimal("4.50")),"renamed @Column projects as Java property");
            check(db.select(ItProduct.class).fields("id","name").fetch().getFirst().get("name").equals("Pencil"),"DSL property selection");
            check(new ItQueries_Bjorm(db).productsByUserName("BJORM").size()==1,"compiled join against real database");
            db.insert(new ItDocument(UUID.randomUUID(),"{\"valid\":true}"));
            check(db.list(ItDocument.class).getFirst().payload().contains("valid"),"PostgreSQL jsonb serialization");
            check(db.update(product)==1,"version update");
            try{db.update(product);throw new AssertionError("stale update not detected");}catch(OptimisticLockException expected){}
            db.tx(tx->tx.batchInsert(List.of(new ItUser(UUID.randomUUID(),"Batch1",1),new ItUser(UUID.randomUUID(),"Batch2",2))));
            check(db.list(ItUser.class,ItUser_.name.in(List.of("Batch1","Batch2"))).size()==2,"batch committed");
            System.out.println("PASS: PostgreSQL CRUD, DSL, version, rollback, batch");
        } finally {
            try(Connection c=ds.getConnection();Statement st=c.createStatement()){
                st.execute("DROP TABLE IF EXISTS bjorm_it_users");
                st.execute("DROP TABLE IF EXISTS bjorm_it_products");
                st.execute("DROP TABLE IF EXISTS bjorm_it_documents");
            }
        }
    }
}
