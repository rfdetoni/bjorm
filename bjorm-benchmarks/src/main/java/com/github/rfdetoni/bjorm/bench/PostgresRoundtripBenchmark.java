package com.github.rfdetoni.bjorm.bench;

import com.github.rfdetoni.bjorm.Bjorm;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * End-to-end real pgJDBC measurement: BJORM and manual JDBC use the same
 * session, SQL columns, driver, prepareThreshold and server-side data.
 *
 * All fixtures are TEMP tables local to one physical JDBC session, so
 * concurrent JMH workers cannot overwrite each other's test data.
 * No permanent database table is created, altered or dropped.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations=2,time=1)
@Measurement(iterations=3,time=1)
@Fork(1)
public class PostgresRoundtripBenchmark {
    @Param({"1","32","256"}) public int childCount;
    /** pgJDBC server-prepared-statement threshold: 0 disables server prepare. */
    @Param({"0","5"}) public int prepareThreshold;
    /** pgJDBC per-connection client-side statement query cache size. */
    @Param({"0","256"}) public int preparedStatementCacheQueries;

    private Connection physical;
    private DataSource source;
    private Bjorm bjorm;
    private UUID flatId;
    private UUID graphId;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        String url=System.getenv("BJORM_BENCH_JDBC_URL");
        if(url==null || !url.startsWith("jdbc:postgresql:"))
            throw new IllegalStateException(
                "Set BJORM_BENCH_JDBC_URL=jdbc:postgresql://.../disposable_db");
        Properties properties=new Properties();
        properties.setProperty("user",System.getenv().getOrDefault("BJORM_BENCH_USER",""));
        properties.setProperty("password",System.getenv().getOrDefault("BJORM_BENCH_PASSWORD",""));
        properties.setProperty("prepareThreshold",Integer.toString(prepareThreshold));
        properties.setProperty("preparedStatementCacheQueries",Integer.toString(preparedStatementCacheQueries));
        physical=DriverManager.getConnection(url,properties);
        // Return a no-op-close connection view; closing BJORM's logical
        // connection must not discard the driver's per-session statement cache.
        // One connection belongs to one JMH thread; no cross-thread sharing.
        Connection logical=(Connection)Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class<?>[]{Connection.class},
            (proxy,method,args)->{
                if("close".equals(method.getName()))return null;
                try {return method.invoke(physical,args);}
                catch(InvocationTargetException failure) {throw failure.getCause();}
            });
        source=new SessionDataSource(logical);
        try(Statement st=physical.createStatement()) {
            st.execute("CREATE TEMP TABLE bjorm_bench (id uuid PRIMARY KEY,label text NOT NULL,quantity integer NOT NULL)");
            st.execute("CREATE TEMP TABLE bjorm_bench_parents (id uuid PRIMARY KEY,label text NOT NULL)");
            st.execute("CREATE TEMP TABLE bjorm_bench_children (id uuid PRIMARY KEY,parentId uuid NOT NULL REFERENCES bjorm_bench_parents(id),label text NOT NULL)");
        }
        flatId=UUID.randomUUID();
        graphId=UUID.randomUUID();
        try(PreparedStatement ps=physical.prepareStatement(
            "INSERT INTO bjorm_bench (id,label,quantity) VALUES (?,?,?)")) {
            ps.setObject(1,flatId);ps.setString(2,"flat");ps.setInt(3,42);
            ps.executeUpdate();
        }
        try(PreparedStatement ps=physical.prepareStatement(
            "INSERT INTO bjorm_bench_parents (id,label) VALUES (?,?)")) {
            ps.setObject(1,graphId);ps.setString(2,"root");
            ps.executeUpdate();
        }
        try(PreparedStatement ps=physical.prepareStatement(
            "INSERT INTO bjorm_bench_children (id,parentId,label) VALUES (?,?,?)")) {
            for(int i=0;i<childCount;i++) {
                ps.setObject(1,UUID.randomUUID());ps.setObject(2,graphId);
                ps.setString(3,"child-"+i);ps.addBatch();
            }
            ps.executeBatch();
        }
        bjorm=Bjorm.open(source,
            BenchItem_BjormMapper.INSTANCE,
            BenchParent_BjormMapper.INSTANCE,
            BenchChild_BjormMapper.INSTANCE);
    }

    @TearDown(Level.Trial)
    public void close() throws SQLException {
        if(physical!=null)physical.close(); // automatically drops session TEMP tables
    }

    @Benchmark
    public BenchItem bjormFlatLookup() {
        return bjorm.find(BenchItem.class,flatId);
    }

    @Benchmark
    public BenchItem manualFlatLookup() throws SQLException {
        try(Connection conn=source.getConnection();
            PreparedStatement ps=conn.prepareStatement(
                "SELECT id,label,quantity FROM bjorm_bench WHERE id = ?")) {
            ps.setObject(1,flatId);
            try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next())return null;
                return new BenchItem(rs.getObject(1,UUID.class),
                    rs.getString(2),rs.getInt(3));
            }
        }
    }

    @Benchmark
    public BenchParent bjormGraphJoin() {
        return bjorm.find(BenchParent.class,graphId);
    }

    /** A single manual LEFT JOIN, not two SELECT statements or an ORM. */
    @Benchmark
    public BenchParent manualGraphJoin() throws SQLException {
        try(Connection conn=source.getConnection();
            PreparedStatement ps=conn.prepareStatement(
                "SELECT p.id,p.label,c.id,c.parentId,c.label "+
                "FROM bjorm_bench_parents p LEFT JOIN bjorm_bench_children c "+
                "ON c.parentId=p.id WHERE p.id = ?")) {
            ps.setObject(1,graphId);
            try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next())return null;
                UUID id=rs.getObject(1,UUID.class);
                String label=rs.getString(2);
                ArrayList<BenchChild> children=new ArrayList<>();
                do {
                    UUID childId=rs.getObject(3,UUID.class);
                    if(childId!=null)children.add(new BenchChild(childId,
                        rs.getObject(4,UUID.class),rs.getString(5)));
                } while(rs.next());
                return new BenchParent(id,label,children);
            }
        }
    }

    private record SessionDataSource(Connection logical) implements DataSource {
        public Connection getConnection() {return logical;}
        public Connection getConnection(String username,String password) throws SQLException {
            throw new SQLException("Benchmark session does not accept credential overrides");
        }
        public PrintWriter getLogWriter() {return null;}
        public void setLogWriter(PrintWriter writer) {}
        public void setLoginTimeout(int seconds) {}
        public int getLoginTimeout() {return 0;}
        public Logger getParentLogger() {return Logger.getGlobal();}
        public <T> T unwrap(Class<T> type) throws SQLException {
            if(type.isInstance(this))return type.cast(this);
            throw new SQLException("Unsupported unwrap");
        }
        public boolean isWrapperFor(Class<?> type) {return type.isInstance(this);}
    }
}
