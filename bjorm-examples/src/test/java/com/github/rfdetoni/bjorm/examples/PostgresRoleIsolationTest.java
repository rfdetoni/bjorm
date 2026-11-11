package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Bjorm;
import com.github.rfdetoni.bjorm.BjormException;
import com.github.rfdetoni.bjorm.BjormOptions;
import com.github.rfdetoni.bjorm.SqlDialects;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * PostgreSQL least-privilege integration test with a real LOGIN credential.
 * Opt in with BJORM_ROLE_TEST_ADMIN=true on an expendable database. The
 * bootstrap account must be able to create/drop a role and schemas.
 */
public final class PostgresRoleIsolationTest {
    private PostgresRoleIsolationTest() {}

    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }

    private static DataSource source(String url,String username,String password) {
        return new DataSource() {
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(url,username,password);
            }
            public Connection getConnection(String user,String pass) throws SQLException {
                return DriverManager.getConnection(url,user,pass);
            }
            public PrintWriter getLogWriter() {return null;}
            public void setLogWriter(PrintWriter writer) {}
            public void setLoginTimeout(int seconds) {}
            public int getLoginTimeout() {return 0;}
            public Logger getParentLogger() {return Logger.getGlobal();}
            public <T> T unwrap(Class<T> type) throws SQLException {throw new SQLException("unsupported");}
            public boolean isWrapperFor(Class<?> type) {return false;}
        };
    }

    private static void expectPermissionDenied(Runnable operation,String message,String... acceptableStates) {
        try {
            operation.run();
            throw new AssertionError(message);
        } catch(BjormException error) {
            Throwable cause=error;
            while(cause!=null && !(cause instanceof SQLException))cause=cause.getCause();
            check(cause instanceof SQLException &&
                java.util.Set.of(acceptableStates).contains(((SQLException)cause).getSQLState()),
                "Expected SQLSTATE "+java.util.Arrays.toString(acceptableStates)+" for "+message);
        }
    }

    public static void main(String[] args) throws Exception {
        String url=System.getenv("BJORM_TEST_JDBC_URL");
        if(url==null || url.isBlank() ||
            !"true".equalsIgnoreCase(System.getenv("BJORM_ROLE_TEST_ADMIN"))) {
            System.out.println("SKIP: PostgreSQL LOGIN isolation requires BJORM_ROLE_TEST_ADMIN=true");
            return;
        }
        String admin=System.getenv().getOrDefault("BJORM_TEST_USER","");
        String adminPassword=System.getenv().getOrDefault("BJORM_TEST_PASSWORD","");
        DataSource bootstrap=source(url,admin,adminPassword);
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12);
        String tenantA="bjorm_role_a_"+suffix;
        String tenantB="bjorm_role_b_"+suffix;
        String role="bjorm_app_"+suffix;
        String credential=UUID.randomUUID().toString().replace("-","");
        UUID shared=UUID.randomUUID();
        try {
            try(Connection conn=bootstrap.getConnection(); Statement st=conn.createStatement()) {
                st.execute("CREATE SCHEMA "+tenantA);
                st.execute("CREATE SCHEMA "+tenantB);
                st.execute("CREATE TABLE "+tenantA+".bjorm_it_users (id uuid PRIMARY KEY,name text NOT NULL,age int NOT NULL)");
                st.execute("CREATE TABLE "+tenantB+".bjorm_it_users (id uuid PRIMARY KEY,name text NOT NULL,age int NOT NULL)");
                st.execute("CREATE ROLE "+role+" LOGIN PASSWORD '"+credential+"'");
                st.execute("REVOKE ALL ON SCHEMA "+tenantA+" FROM PUBLIC");
                st.execute("REVOKE ALL ON SCHEMA "+tenantB+" FROM PUBLIC");
                st.execute("GRANT USAGE ON SCHEMA "+tenantA+" TO "+role);
                st.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON "+tenantA+".bjorm_it_users TO "+role);
                try(PreparedStatement ps=conn.prepareStatement(
                    "INSERT INTO "+tenantA+".bjorm_it_users (id,name,age) VALUES (?, ?, ?)")) {
                    ps.setObject(1,shared);ps.setString(2,"allowed");ps.setInt(3,10);ps.executeUpdate();
                }
                try(PreparedStatement ps=conn.prepareStatement(
                    "INSERT INTO "+tenantB+".bjorm_it_users (id,name,age) VALUES (?, ?, ?)")) {
                    ps.setObject(1,shared);ps.setString(2,"restricted");ps.setInt(3,20);ps.executeUpdate();
                }
            }
            DataSource restricted=source(url,role,credential);
            try(Connection conn=restricted.getConnection(); Statement st=conn.createStatement();
                ResultSet result=st.executeQuery("SELECT current_user")) {
                check(result.next() && role.equals(result.getString(1)),
                    "Application must authenticate as its own LOGIN role, not SET ROLE from admin");
            }
            Bjorm allowedRegistry=Bjorm.tenantRequired(
                java.util.Map.of(tenantA,restricted),
                BjormOptions.defaults(),SqlDialects.POSTGRESQL,ItUser_BjormMapper.INSTANCE);
            check("allowed".equals(allowedRegistry.forTenant(tenantA)
                .find(ItUser.class,shared).name()),
                "Registry routes to the restricted PostgreSQL LOGIN connection");
            try {allowedRegistry.forTenant(tenantB);
                throw new AssertionError("Unregistered PostgreSQL tenant pool was selected");}
            catch(SecurityException expected) {}
            Bjorm root=Bjorm.tenantRequired(restricted,BjormOptions.defaults(),
                SqlDialects.POSTGRESQL,ItUser_BjormMapper.INSTANCE);
            Bjorm permitted=root.forTenant(tenantA);
            Bjorm other=root.forTenant(tenantB);
            check("allowed".equals(permitted.find(ItUser.class,shared).name()),
                "Least-privilege role can read its authorized tenant");
            UUID extra=UUID.randomUUID();
            permitted.insert(new ItUser(extra,"permitted write",33));
            check("permitted write".equals(permitted.find(ItUser.class,extra).name()),
                "Least-privilege role can insert into its authorized tenant");
            expectPermissionDenied(()->other.find(ItUser.class,shared),
                "A-scoped credential must not read tenant B, even if scoped to B", "42501", "42P01");
            expectPermissionDenied(()->permitted.permitReviewedTenantNativeSql().execute(
                "UPDATE "+tenantB+".bjorm_it_users SET age = ? WHERE id = ?",
                permitted.params(999,shared)),
                "qualified native SQL must not write tenant B with an A-only LOGIN role", "42501");
            try(Connection conn=bootstrap.getConnection();
                PreparedStatement ps=conn.prepareStatement(
                    "SELECT age FROM "+tenantB+".bjorm_it_users WHERE id = ?")) {
                ps.setObject(1,shared);
                try(ResultSet rs=ps.executeQuery()) {
                    check(rs.next() && rs.getInt(1)==20,
                        "Unauthorized cross-tenant UPDATE left tenant B unchanged");
                }
            }
            System.out.println("PASS: PostgreSQL per-tenant LOGIN role grants block qualified cross-schema reads/writes");
        } finally {
            try(Connection conn=bootstrap.getConnection();Statement st=conn.createStatement()) {
                st.execute("DROP SCHEMA IF EXISTS "+tenantA+" CASCADE");
                st.execute("DROP SCHEMA IF EXISTS "+tenantB+" CASCADE");
                st.execute("DROP ROLE IF EXISTS "+role);
            }
        }
    }
}
