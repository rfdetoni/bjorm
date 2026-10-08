package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.*;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/** Executed only with disposable MySQL 8.4 DB; intentionally owns its fixture tables. */
public final class MySqlIntegrationTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        String url=System.getenv("BJORM_MYSQL_JDBC_URL");
        if(url==null||url.isBlank()){System.out.println("SKIP: BJORM_MYSQL_JDBC_URL not configured");return;}
        String user=System.getenv().getOrDefault("BJORM_MYSQL_USER","bjorm");
        String pass=System.getenv().getOrDefault("BJORM_MYSQL_PASSWORD","bjorm_test");
        DataSource source=new DataSource(){
            public Connection getConnection()throws SQLException{return DriverManager.getConnection(url,user,pass);}
            public Connection getConnection(String u,String p)throws SQLException{return DriverManager.getConnection(url,u,p);}
            public PrintWriter getLogWriter(){return null;}
            public void setLogWriter(PrintWriter w){}
            public void setLoginTimeout(int v){}
            public int getLoginTimeout(){return 0;}
            public Logger getParentLogger(){return Logger.getGlobal();}
            public <T>T unwrap(Class<T> cls)throws SQLException{throw new SQLException("Unsupported");}
            public boolean isWrapperFor(Class<?> cls){return false;}
        };
        try(Connection c=source.getConnection();Statement st=c.createStatement()){
            st.execute("CREATE TABLE bjorm_it_users(id CHAR(36) PRIMARY KEY,name VARCHAR(255),age INT NOT NULL)");
            st.execute("CREATE TABLE bjorm_it_documents(id CHAR(36) PRIMARY KEY,payload JSON)");
            st.execute("CREATE TABLE bjorm_it_auto_long(id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,label VARCHAR(255))");
            st.execute("CREATE TABLE bjorm_it_orders(id CHAR(36) PRIMARY KEY,description VARCHAR(255))");
            st.execute("CREATE TABLE bjorm_it_order_lines(id CHAR(36) PRIMARY KEY,orderId CHAR(36),sku VARCHAR(255),FOREIGN KEY(orderId) REFERENCES bjorm_it_orders(id))");
            st.execute("CREATE TABLE bjorm_it_order_notes(id CHAR(36) PRIMARY KEY,orderId CHAR(36),note VARCHAR(255),version INT NOT NULL DEFAULT 0,FOREIGN KEY(orderId) REFERENCES bjorm_it_orders(id))");
        }
        try{
            Bjorm db=Bjorm.open(source,BjormOptions.defaults(),SqlDialects.MYSQL,
                ItUser_BjormMapper.INSTANCE,ItDocument_BjormMapper.INSTANCE,
                AutoLongRecord_BjormMapper.INSTANCE,ItOrder_BjormMapper.INSTANCE,
                ItOrderLine_BjormMapper.INSTANCE,ItOrderNote_BjormMapper.INSTANCE);
            ItUser original=db.insertReturning(new ItUser(null,"MySQL user",33));
            check(original.id()!=null && original.id().version()==7,"v7 generated");
            check(db.find(ItUser.class,original.id()).age()==33,"MySQL UUID read from CHAR(36)");
            db.upsertReturning(new ItUser(original.id(),"Updated",34));
            check(db.find(ItUser.class,original.id()).name().equals("Updated"),"MySQL native upsert");
            check(db.select(ItUser.class).where(ItUser_.id.eq(original.id())).fetch().size()==1,"MySQL UUID predicate bind");
            ItDocument json=db.insertReturning(new ItDocument(null,"{\"count\":2}"));
            check(db.find(ItDocument.class,json.id()).payload().contains("count"),"MySQL native JSON");
            AutoLongRecord serial=db.insertReturning(new AutoLongRecord(null,"serial"));
            check(serial.id()!=null&&serial.id()>0,"MySQL AUTO_INCREMENT key");
            ItOrder parent=new ItOrder();parent.setDescription("mysql graph");
            ItOrderLine child=new ItOrderLine();child.setSku("SKU");parent.getLines().add(child);
            db.insert(parent);
            ItOrder loaded=db.find(ItOrder.class,parent.getId());
            check(loaded!=null&&loaded.getLines().size()==1,"MySQL automatic relation JOIN");
            db.delete(parent);
            check(db.find(ItOrder.class,parent.getId())==null,"MySQL cascade delete");
            System.out.println("PASS: MySQL 8.4 CRUD, native upsert, JSON, UUID v7, AUTO_INCREMENT and eager JOIN graph");
        }finally {
            try(Connection c=source.getConnection();Statement st=c.createStatement()){
                for(String name:List.of("bjorm_it_order_notes","bjorm_it_order_lines","bjorm_it_orders",
                    "bjorm_it_documents","bjorm_it_auto_long","bjorm_it_users"))
                    st.execute("DROP TABLE IF EXISTS "+name);
            }
        }
    }
}
