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
 * Real MySQL accounts, not SET ROLE or mocks, prove that a tenant-scoped
 * runtime credential cannot access another tenant's database even via
 * explicitly reviewed native SQL.
 *
 * Runs only with BJORM_MYSQL_ROLE_TEST_ADMIN=true against an expendable MySQL
 * server with CREATE USER, CREATE DATABASE and GRANT privileges.
 */
public final class MySqlRoleIsolationTest {
    private MySqlRoleIsolationTest() {}

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static DataSource source(String url, String user, String password) {
        return new DataSource() {
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(url, user, password);
            }
            public Connection getConnection(String username, String secret) throws SQLException {
                return DriverManager.getConnection(url, username, secret);
            }
            public PrintWriter getLogWriter() { return null; }
            public void setLogWriter(PrintWriter writer) {}
            public void setLoginTimeout(int seconds) {}
            public int getLoginTimeout() { return 0; }
            public Logger getParentLogger() { return Logger.getGlobal(); }
            public <T> T unwrap(Class<T> type) throws SQLException {
                throw new SQLException("unsupported");
            }
            public boolean isWrapperFor(Class<?> type) { return false; }
        };
    }

    /** A tenant credential must fail with a database privilege error, not a client-side assertion. */
    private static void expectDenied(Runnable operation, String description) {
        try {
            operation.run();
        } catch (BjormException error) {
            Throwable cause = error;
            while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
            check(cause instanceof SQLException, "Expected JDBC denial for " + description);
            SQLException sql = (SQLException) cause;
            check(sql.getErrorCode() == 1044 || sql.getErrorCode() == 1142 ||
                    sql.getErrorCode() == 1143,
                "Expected a MySQL permission denial, got error code " +
                    sql.getErrorCode() + " for " + description);
            return;
        }
        throw new AssertionError("Cross-tenant access succeeded: " + description);
    }

    /** Keep the database URL's connection options, but replace its default catalog. */
    private static String databaseUrl(String baseUrl, String database) {
        int query = baseUrl.indexOf('?');
        int end = query < 0 ? baseUrl.length() : query;
        int separator = baseUrl.lastIndexOf('/', end - 1);
        if (separator < "jdbc:mysql://".length())
            throw new IllegalArgumentException("Expected JDBC MySQL URL with a database path");
        return baseUrl.substring(0, separator + 1) + database + baseUrl.substring(end);
    }

    private static void verifyCredential(String url, String user, String password, String database)
            throws SQLException {
        try (Connection conn = source(url, user, password).getConnection();
             Statement statement = conn.createStatement();
             ResultSet result = statement.executeQuery("SELECT CURRENT_USER(), DATABASE()")) {
            check(result.next(), "MySQL credential introspection returned no row");
            check(result.getString(1).startsWith(user + "@"),
                "Connection must use its own restricted account");
            check(database.equals(result.getString(2)), "Connection must start in its own catalog");
        }
    }

    private static void verifyTenant(DataSource source, String own, String foreign, UUID id)
            throws SQLException {
        Bjorm root = Bjorm.tenantRequired(source, BjormOptions.defaults(),
            SqlDialects.MYSQL, ItUser_BjormMapper.INSTANCE);
        Bjorm allowed = root.forTenant(own);
        Bjorm forbidden = root.forTenant(foreign);
        check("own".equals(allowed.find(ItUser.class, id).name()),
            "Credential should read its own mapped record");
        UUID inserted = UUID.randomUUID();
        allowed.insert(new ItUser(inserted, "inserted", 10));
        check("inserted".equals(allowed.find(ItUser.class, inserted).name()),
            "Credential should write its own mapped record");
        expectDenied(() -> forbidden.find(ItUser.class, id),
            "generated SELECT after foreign forTenant");
        Bjorm reviewed = allowed.permitReviewedTenantNativeSql();
        expectDenied(() -> reviewed.one(
                "SELECT name FROM " + foreign + ".bjorm_it_users WHERE id = ?",
                reviewed.params(id), rs -> rs.getString(1)),
            "qualified SELECT via reviewed native SQL");
        expectDenied(() -> reviewed.execute(
                "INSERT INTO " + foreign + ".bjorm_it_users(id,name,age) VALUES (?,?,?)",
                reviewed.params(UUID.randomUUID(), "cross", 1)),
            "qualified INSERT via reviewed native SQL");
        expectDenied(() -> reviewed.execute(
                "UPDATE " + foreign + ".bjorm_it_users SET age=? WHERE id=?",
                reviewed.params(999, id)),
            "qualified UPDATE via reviewed native SQL");
        expectDenied(() -> reviewed.execute(
                "DELETE FROM " + foreign + ".bjorm_it_users WHERE id=?",
                reviewed.params(id)),
            "qualified DELETE via reviewed native SQL");
        check("own".equals(allowed.find(ItUser.class, id).name()),
            "Failed foreign-catalog access must not poison later local operations");
    }

    public static void main(String[] args) throws Exception {
        String url = System.getenv("BJORM_MYSQL_ROLE_TEST_JDBC_URL");
        if (url == null || url.isBlank()) url = System.getenv("BJORM_MYSQL_JDBC_URL");
        if (url == null || url.isBlank() ||
            !"true".equalsIgnoreCase(System.getenv("BJORM_MYSQL_ROLE_TEST_ADMIN"))) {
            System.out.println("SKIP: MySQL account isolation requires BJORM_MYSQL_ROLE_TEST_ADMIN=true");
            return;
        }
        String adminUser = System.getenv("BJORM_MYSQL_ADMIN_USER");
        String adminPassword = System.getenv("BJORM_MYSQL_ADMIN_PASSWORD");
        if (adminUser == null || adminUser.isBlank() || adminPassword == null ||
            adminPassword.isBlank())
            throw new IllegalStateException("MySQL administrative test credentials are required");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String a = "bjorm_tenant_a_" + suffix;
        String b = "bjorm_tenant_b_" + suffix;
        String userA = "bjorm_a_" + suffix;
        String userB = "bjorm_b_" + suffix;
        String passwordA = UUID.randomUUID().toString().replace("-", "");
        String passwordB = UUID.randomUUID().toString().replace("-", "");
        DataSource admin = source(url, adminUser, adminPassword);
        UUID id = UUID.randomUUID();
        try {
            try (Connection conn = admin.getConnection(); Statement statement = conn.createStatement()) {
                statement.execute("CREATE DATABASE " + a);
                statement.execute("CREATE DATABASE " + b);
                statement.execute("CREATE TABLE " + a +
                    ".bjorm_it_users(id CHAR(36) PRIMARY KEY,name VARCHAR(255) NOT NULL,age INT NOT NULL)");
                statement.execute("CREATE TABLE " + b +
                    ".bjorm_it_users(id CHAR(36) PRIMARY KEY,name VARCHAR(255) NOT NULL,age INT NOT NULL)");
                statement.execute("CREATE USER '" + userA + "'@'%' IDENTIFIED BY '" + passwordA + "'");
                statement.execute("CREATE USER '" + userB + "'@'%' IDENTIFIED BY '" + passwordB + "'");
                statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + a + ".* TO '" + userA + "'@'%'");
                statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + b + ".* TO '" + userB + "'@'%'");
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + a + ".bjorm_it_users(id,name,age) VALUES (?,?,?)")) {
                    ps.setString(1, id.toString());
                    ps.setString(2, "own");
                    ps.setInt(3, 20);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + b + ".bjorm_it_users(id,name,age) VALUES (?,?,?)")) {
                    ps.setString(1, id.toString());
                    ps.setString(2, "own");
                    ps.setInt(3, 30);
                    ps.executeUpdate();
                }
            }
            String urlA = databaseUrl(url, a);
            String urlB = databaseUrl(url, b);
            verifyCredential(urlA, userA, passwordA, a);
            verifyCredential(urlB, userB, passwordB, b);
            verifyTenant(source(urlA, userA, passwordA), a, b, id);
            verifyTenant(source(urlB, userB, passwordB), b, a, id);
            try (Connection conn = admin.getConnection();
                 Statement statement = conn.createStatement()) {
                try (ResultSet result = statement.executeQuery(
                    "SELECT age FROM " + a + ".bjorm_it_users WHERE id='" + id + "'")) {
                    check(result.next() && result.getInt(1) == 20,
                        "Denied operations must not change tenant A");
                }
                try (ResultSet result = statement.executeQuery(
                    "SELECT age FROM " + b + ".bjorm_it_users WHERE id='" + id + "'")) {
                    check(result.next() && result.getInt(1) == 30,
                        "Denied operations must not change tenant B");
                }
            }
            System.out.println("PASS: restricted MySQL logins enforce bilateral tenant isolation for CRUD");
        } finally {
            // Resources are unique to this test; attempt every cleanup even if a prior step failed.
            SQLException cleanupFailure = null;
            try (Connection conn = admin.getConnection(); Statement statement = conn.createStatement()) {
                for (String sql : new String[] {
                    "DROP USER IF EXISTS '" + userA + "'@'%'",
                    "DROP USER IF EXISTS '" + userB + "'@'%'",
                    "DROP DATABASE IF EXISTS " + a,
                    "DROP DATABASE IF EXISTS " + b
                }) {
                    try {
                        statement.execute(sql);
                    } catch (SQLException error) {
                        if (cleanupFailure == null) cleanupFailure = error;
                        else cleanupFailure.addSuppressed(error);
                    }
                }
            }
            if (cleanupFailure != null) throw cleanupFailure;
        }
    }
}
