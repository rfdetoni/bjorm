package com.github.rfdetoni.bjorm;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Explicit, immutable instance-owned mapper registry, no globally scoped context. */
public final class Bjorm implements Operations {
    private final DataSource dataSource;
    /** Immutable opt-in registry; absent in legacy single-DataSource routing. */
    private final Map<String,DataSource> tenantPools;
    private final BjormOptions options;
    private final SqlDialect dialect;
    private final Map<Class<?>, EntityMapper<?>> mappers;
    private final boolean tenantRequired;
    private final String tenantSchema;
    private final SqlObserver sqlObserver;
    private final boolean reviewedTenantNativeSql;
    private static final java.util.regex.Pattern TENANT_IDENTIFIER =
        java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z_0-9]{0,62}");

    /** Immutable tenant-bound view. Never share an unscoped connection in tenant-required mode. */
    private Bjorm(Bjorm source, String tenantSchema) {
        this.dataSource=source.tenantPools!=null && tenantSchema!=null
            ?Objects.requireNonNull(source.tenantPools.get(tenantSchema),"Unregistered tenant DataSource")
            :source.dataSource;
        this.tenantPools=source.tenantPools;
        this.options=source.options;
        this.dialect=source.dialect;
        this.mappers=source.mappers;
        this.tenantRequired=true;
        this.tenantSchema=tenantSchema;
        this.sqlObserver=source.sqlObserver;
        this.reviewedTenantNativeSql=source.reviewedTenantNativeSql;
    }
    private Bjorm(Bjorm source,boolean allowNativeSql) {
        this.dataSource=source.dataSource;
        this.tenantPools=source.tenantPools;
        this.options=source.options;
        this.dialect=source.dialect;
        this.mappers=source.mappers;
        this.tenantRequired=source.tenantRequired;
        this.tenantSchema=source.tenantSchema;
        this.sqlObserver=source.sqlObserver;
        this.reviewedTenantNativeSql=allowNativeSql;
    }
    private Bjorm(Bjorm source,SqlObserver observer) {
        this.dataSource=source.dataSource;
        this.tenantPools=source.tenantPools;
        this.options=source.options;
        this.dialect=source.dialect;
        this.mappers=source.mappers;
        this.tenantRequired=source.tenantRequired;
        this.tenantSchema=source.tenantSchema;
        this.sqlObserver=Objects.requireNonNull(observer,"observer");
        this.reviewedTenantNativeSql=source.reviewedTenantNativeSql;
    }
    private Bjorm(DataSource dataSource,BjormOptions options,SqlDialect dialect,EntityMapper<?>... registered) {
        this(dataSource,null,options,dialect,registered);
    }
    private Bjorm(DataSource dataSource,Map<String,DataSource> tenantPools,
                  BjormOptions options,SqlDialect dialect,EntityMapper<?>... registered) {
        this.dataSource = tenantPools==null?Objects.requireNonNull(dataSource,"dataSource"):null;
        this.tenantPools=tenantPools;
        this.options = Objects.requireNonNull(options, "options");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        Map<Class<?>, EntityMapper<?>> byType = new HashMap<>();
        for(EntityMapper<?> m : registered) if(byType.putIfAbsent(m.type(), m)!=null)
            throw new IllegalArgumentException("Duplicate mapper: " + m.type());
        mappers = Map.copyOf(byType);
        tenantRequired=false;
        tenantSchema=null;
        sqlObserver=null;
        reviewedTenantNativeSql=false;
    }
    /** Discover compile-time generated mappers once at startup via Java ServiceLoader. */
    public static Bjorm open(DataSource dataSource) {
        ArrayList<EntityMapper<?>> discovered=new ArrayList<>();
        for(EntityMapper<?> mapper:ServiceLoader.load(EntityMapper.class))discovered.add(mapper);
        return new Bjorm(dataSource,BjormOptions.defaults(),SqlDialects.POSTGRESQL,discovered.toArray(EntityMapper<?>[]::new));
    }
    /** Explicit registration avoids discovery and works with custom mapper implementations. */
    public static Bjorm open(DataSource dataSource,BjormOptions options) {
        ArrayList<EntityMapper<?>> discovered=new ArrayList<>();
        for(EntityMapper<?> mapper:ServiceLoader.load(EntityMapper.class))discovered.add(mapper);
        return new Bjorm(dataSource,options,SqlDialects.POSTGRESQL,discovered.toArray(EntityMapper<?>[]::new));
    }
    public static Bjorm open(DataSource dataSource, EntityMapper<?>... mappers) {return new Bjorm(dataSource,BjormOptions.defaults(),SqlDialects.POSTGRESQL,mappers);}
    public static Bjorm open(DataSource source,BjormOptions options,EntityMapper<?>... mappers) {
        return new Bjorm(source,options,SqlDialects.POSTGRESQL,mappers);
    }
    public static Bjorm open(DataSource source,BjormOptions options,SqlDialect dialect,EntityMapper<?>... mappers){
        if(mappers.length==0) {
            ArrayList<EntityMapper<?>> discovered=new ArrayList<>();
            for(EntityMapper<?> mapper:ServiceLoader.load(EntityMapper.class))discovered.add(mapper);
            return new Bjorm(source,options,dialect,discovered.toArray(EntityMapper<?>[]::new));
        }
        return new Bjorm(source,options,dialect,mappers);
    }
    /** A root BJORM instance that refuses all JDBC calls until explicitly scoped to a tenant. */
    public static Bjorm tenantRequired(DataSource source, BjormOptions options,
                                       SqlDialect dialect, EntityMapper<?>... registered) {
        return new Bjorm(open(source,options,dialect,registered),(String)null);
    }
    public static Bjorm tenantRequired(DataSource source) {
        return new Bjorm(open(source),(String)null);
    }
    /**
     * Stronger tenant isolation: each authorized schema uses a dedicated, already
     * provisioned DataSource and database role. The unscoped root has NO
     * fallback connection; unknown tenants are rejected before JDBC checkout.
     *
     * Supply only tenants authorized for this root. The immutable registry is
     * a snapshot: construct a new root to rotate or revoke a pool.
     */
    public static Bjorm tenantRequired(Map<String,DataSource> isolatedPools,
                                       BjormOptions options,SqlDialect dialect,
                                       EntityMapper<?>... registered) {
        Objects.requireNonNull(isolatedPools,"isolatedPools");
        if(isolatedPools.isEmpty())throw new IllegalArgumentException("At least one tenant DataSource is required");
        Map<String,DataSource> verified=new HashMap<>();
        for(Map.Entry<String,DataSource> entry:isolatedPools.entrySet()) {
            validateTenantIdentifier(entry.getKey());
            verified.put(entry.getKey(),Objects.requireNonNull(entry.getValue(),"tenant DataSource"));
        }
        EntityMapper<?>[] mappers=registered;
        if(mappers.length==0) {
            ArrayList<EntityMapper<?>> discovered=new ArrayList<>();
            for(EntityMapper<?> mapper:ServiceLoader.load(EntityMapper.class))discovered.add(mapper);
            mappers=discovered.toArray(EntityMapper<?>[]::new);
        }
        Bjorm unscoped=new Bjorm(null,Map.copyOf(verified),options,dialect,mappers);
        return new Bjorm(unscoped,(String)null);
    }
    private static void validateTenantIdentifier(String schema) {
        Objects.requireNonNull(schema,"schema");
        if(!TENANT_IDENTIFIER.matcher(schema).matches() ||
           schema.toLowerCase(Locale.ROOT).startsWith("pg_") || Set.of("public","information_schema","mysql",
               "performance_schema","sys","pg_catalog").contains(schema.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Unsafe tenant schema identifier");
    }
    /** Tenant schema must come from an authorized server-side tenant registry, not request input. */
    public Bjorm forTenant(String schema) {
        if (!tenantRequired) throw new IllegalStateException("forTenant requires a tenantRequired BJORM root");
        validateTenantIdentifier(schema);
        if(tenantSchema!=null)throw new IllegalStateException("Cannot rebind an already-scoped BJORM instance");
        if(tenantPools!=null && !tenantPools.containsKey(schema))
            throw new SecurityException("Tenant is not registered in this isolated DataSource root");
        // Static schema SQL bypasses search_path and could cross tenant boundaries.
        // Refuse the entire mapper registry, not just an individual operation.
        for(EntityMapper<?> mapper:mappers.values()) {
            if(mapper.staticSchema()!=null && !mapper.staticSchema().isBlank())
                throw new IllegalStateException("Fixed-schema @Table mapping cannot be used with tenantRequired BJORM");
            // Custom mapper implementations must not bypass the generated metadata check.
            if(mapper.table()==null || !TENANT_IDENTIFIER.matcher(mapper.table()).matches())
                throw new IllegalStateException("Tenant-required mappers must target unqualified, safe table identifiers");
        }
        return new Bjorm(this,schema);
    }
    public SqlDialect dialect(){return dialect;}
    /** Returns an immutable context with execution timing hooks; no SQL or tenant IDs are emitted. */
    public Bjorm observeSql(SqlObserver observer) {return new Bjorm(this,observer);}
    /**
     * Explicitly opt in to arbitrary native SQL for an already authorized
     * tenant. Only use in vetted server code with least-privileged DB roles.
     * Unlike generated mapped SQL, native SQL can qualify another schema or
     * execute SET ROLE, so it is denied in tenant-required contexts by default.
     */
    public Bjorm permitReviewedTenantNativeSql() {
        if(!tenantRequired || tenantSchema==null)
            throw new IllegalStateException("Native SQL exception requires an authorized scoped tenant");
        return new Bjorm(this,true);
    }
    private void requireNativeSqlPermission() {
        requireTenant();
        if(tenantRequired && !reviewedTenantNativeSql)
            throw new SecurityException("Native SQL disabled for tenant; use mapped DSL or explicit reviewed permission");
    }
    private static void notifyObserver(SqlObserver observer,SqlOperation operation,
                                      long start,boolean succeeded) {
        try {observer.completed(operation,System.nanoTime()-start,succeeded);}
        catch(RuntimeException ignored) {
            // Telemetry must never mask JDBC errors or change write/rollback behavior.
        }
    }
    private ResultSet executeQueryObserved(PreparedStatement ps) throws SQLException {
        SqlObserver observer=sqlObserver;
        if(observer==null)return ps.executeQuery();
        long start=System.nanoTime();boolean ok=false;
        try {ResultSet rs=ps.executeQuery();ok=true;return rs;}
        finally {notifyObserver(observer,SqlOperation.QUERY,start,ok);}
    }
    private int executeUpdateObserved(PreparedStatement ps) throws SQLException {
        SqlObserver observer=sqlObserver;
        if(observer==null)return ps.executeUpdate();
        long start=System.nanoTime();boolean ok=false;
        try {int count=ps.executeUpdate();ok=true;return count;}
        finally {notifyObserver(observer,SqlOperation.UPDATE,start,ok);}
    }
    private int[] executeBatchObserved(PreparedStatement ps) throws SQLException {
        SqlObserver observer=sqlObserver;
        if(observer==null)return ps.executeBatch();
        long start=System.nanoTime();boolean ok=false;
        try {int[] counts=ps.executeBatch();ok=true;return counts;}
        finally {notifyObserver(observer,SqlOperation.BATCH,start,ok);}
    }
    private PreparedStatement prepare(Connection c,String sql) throws SQLException {
        PreparedStatement ps=c.prepareStatement(sql);
        try {if(options.queryTimeoutSeconds()>0)ps.setQueryTimeout(options.queryTimeoutSeconds());return ps;}
        catch(SQLException|RuntimeException e){try{ps.close();}catch(SQLException ex){e.addSuppressed(ex);}throw e;}
    }
    private PreparedStatement prepare(Connection c,String sql,String[] keyColumns) throws SQLException {
        PreparedStatement ps=c.prepareStatement(sql,keyColumns);
        try {if(options.queryTimeoutSeconds()>0)ps.setQueryTimeout(options.queryTimeoutSeconds());return ps;}
        catch(SQLException|RuntimeException e){try{ps.close();}catch(SQLException ex){e.addSuppressed(ex);}throw e;}
    }
    /** Apply fetch hints only to queries, never to INSERT/UPDATE/DELETE statements. */
    private PreparedStatement prepareRead(Connection c,String sql) throws SQLException {
        PreparedStatement ps=prepare(c,sql);
        try {ps.setFetchSize(options.fetchSize());return ps;}
        catch(SQLException|RuntimeException e){try{ps.close();}catch(SQLException ex){e.addSuppressed(ex);}throw e;}
    }
    /** Prevent the JDBC driver itself from buffering an unbounded SELECT result. */
    private void boundRows(PreparedStatement ps) throws SQLException {
        if(options.maxBufferedRows()<Integer.MAX_VALUE)
            ps.setMaxRows(options.maxBufferedRows()+1); // lookahead detects and rejects truncation
    }
    private void checkRows(int count) {
        if(count>options.maxBufferedRows())throw new IllegalStateException(
            "Query exceeded maxBufferedRows="+options.maxBufferedRows()+"; use a paged query or forEach/scan");
    }
    public BjormOptions options(){return options;}
    @SuppressWarnings("unchecked")
    private <T> EntityMapper<T> mapper(Class<?> type) {
        EntityMapper<?> mapper = mappers.get(type);
        if(mapper==null) throw new IllegalArgumentException("No generated mapper registered for " + type.getName());
        return (EntityMapper<T>)mapper;
    }
    @FunctionalInterface private interface JdbcWork<R> {R run(Connection c) throws SQLException;}
    private void requireTenant() {
        if(tenantRequired && tenantSchema==null)
            throw new IllegalStateException("Tenant required: call forTenant(authorizedSchema) before a JDBC operation");
    }
    private boolean postgresTenant() {
        return tenantRequired && "postgresql".equals(dialect.name());
    }
    private boolean mysqlTenant() {
        return tenantRequired && "mysql".equals(dialect.name());
    }
    /** SET LOCAL is transaction-scoped; omitting public prevents accidental cross-schema fallback. */
    private void applyPostgresTenant(Connection c) throws SQLException {
        try (Statement statement=c.createStatement()) {
            statement.execute("SET LOCAL search_path TO pg_catalog, \"" + tenantSchema + "\", pg_temp");
        }
    }
    private <R> R withConnection(JdbcWork<R> work) {
        return withConnection(work,false);
    }
    /** A row lock must never create its own short-lived implicit tenant transaction. */
    private <R> R withConnection(JdbcWork<R> work,boolean requireExistingTransaction) {
        requireTenant();
        try(Connection c=dataSource.getConnection()) {
            if(requireExistingTransaction && c.getAutoCommit())
                throw new IllegalStateException("BJORM row locking requires a caller-owned active transaction");
            if(!tenantRequired)return work.run(c);
            if(postgresTenant()) {
                boolean ownTransaction=c.getAutoCommit();
                if(ownTransaction)c.setAutoCommit(false);
                try {
                    applyPostgresTenant(c);
                    R result=work.run(c);
                    if(ownTransaction)c.commit();
                    return result;
                } catch(SQLException|RuntimeException|Error e) {
                    if(ownTransaction)try {c.rollback();}catch(SQLException ex){e.addSuppressed(ex);}
                    throw e;
                } finally {
                    if(ownTransaction)c.setAutoCommit(true);
                }
            }
            if(mysqlTenant()) {
                String previous=c.getCatalog();
                if(previous==null || previous.isBlank())
                    throw new IllegalStateException("Cannot restore MySQL catalog: connection has no original catalog");
                try {
                    c.setCatalog(tenantSchema);
                    if(!tenantSchema.equals(c.getCatalog()))
                        throw new IllegalStateException("JDBC driver did not activate the tenant catalog");
                    return work.run(c);
                } finally {
                    c.setCatalog(previous);
                }
            }
            throw new UnsupportedOperationException("Tenant isolation not supported by dialect: "+dialect.name());
        } catch(SQLException e) {throw new BjormException("JDBC operation failed",e);}
    }
    public <T> void insert(T entity) {
        requireReturnForImmutable(entity);
        insertReturning(entity);
    }
    private <T> void requireReturnForImmutable(T entity) {
        Objects.requireNonNull(entity);
        EntityMapper<T> m=mapper(entity.getClass());
        if(m.immutable() && (m.generatedId() || m.id(entity)==null))
            throw new IllegalArgumentException("Immutable entity requires insertReturning()/upsertReturning() to receive its generated @Id: "+m.type().getName());
    }
    public <T> T insertReturning(T entity) {
        Objects.requireNonNull(entity);
        if(mapper(entity.getClass()).children().isEmpty()) return withConnection(c->insert(c,entity));
        return operationTxResult(tx->tx.insertReturning(entity));
    }
    /** Build the entire detached graph before acquiring a JDBC connection. */
    private <T> T detachedWriteCopy(T entity) {
        return detachedWriteCopy(Objects.requireNonNull(entity,"entity"),
            new IdentityHashMap<>());
    }
    private <T> T detachedWriteCopy(T entity,IdentityHashMap<Object,Boolean> seen) {
        if(seen.put(entity,Boolean.TRUE)!=null)
            throw new IllegalArgumentException("Cycle or repeated entity in detached persistence graph");
        EntityMapper<T> mapper=mapper(entity.getClass());
        if(!mapper.detachedChildrenSupported())
            throw new UnsupportedOperationException(
                "Detached write requires generated child collection replacement for "+mapper.type().getName());
        T copy=mapper.copyForWrite(entity);
        for(ChildRelation<T> relation:mapper.children()) {
            Iterable<?> sourceChildren=relation.children(entity);
            ArrayList<Object> copies=new ArrayList<>();
            if(sourceChildren!=null)for(Object child:sourceChildren) {
                Objects.requireNonNull(child,"Detached child cannot be null");
                if(!relation.childType().isInstance(child))
                    throw new IllegalArgumentException("Unexpected detached child type: "+child.getClass());
                copies.add(detachedChildCopy(child,seen));
            }
            copy=Objects.requireNonNull(mapper.withChildren(copy,relation.property(),copies),
                "Generated detached child replacement returned null");
        }
        return copy;
    }
    @SuppressWarnings("unchecked")
    private Object detachedChildCopy(Object child,IdentityHashMap<Object,Boolean> seen) {
        return detachedWriteCopy(child,seen);
    }
    public <T> T insertDetached(T entity) {
        return insertReturning(detachedWriteCopy(entity));
    }
    public <T> T upsertDetached(T entity) {
        return upsertReturning(detachedWriteCopy(entity));
    }
    public <T> int update(T entity) {Objects.requireNonNull(entity);return withConnection(c->update(c,entity));}
    public <T> int delete(T entity) {
        Objects.requireNonNull(entity);
        if(mapper(entity.getClass()).children().isEmpty()) return withConnection(c->delete(c,entity));
        return operationTxResult(tx->tx.delete(entity));
    }
    public <T> int upsert(T entity) {
        requireReturnForImmutable(entity);
        EntityMapper<T> m=mapper(entity.getClass());
        // Versioned MySQL UPSERT uses a guarded atomic statement followed
        // by a readback in a transaction; participate in Spring's boundary.
        if(m.optimisticLocking() && "mysql".equals(dialect.name()))
            return operationTxResult(tx->tx.upsert(entity));
        if(m.children().isEmpty()) return withConnection(c->upsert(c,entity));
        return txResult(tx->tx.upsert(entity));
    }
    public <T> T upsertReturning(T entity) {
        Objects.requireNonNull(entity);
        EntityMapper<T> m=mapper(entity.getClass());
        if(m.children().isEmpty() && !m.upsertNeedsReload() && !m.optimisticLocking()) return withConnection(c->{
            T prepared=m.materializeUpsert(entity);
            upsertPrepared(c,prepared);
            return prepared;
        });
        return operationTxResult(tx->tx.upsertReturning(entity));
    }
    public <T> T find(Class<T> type,Object id) {
        Objects.requireNonNull(id,"id required for find()");
        return withConnection(c->find(c,type,id));
    }
    public <T> List<T> list(Class<T> type,SqlPredicate where) {return select(type).whereNullable(where).fetch();}
    /** Native count/exists use generated table mapping and typed, bound predicates. */
    public <T> long count(Class<T> type, SqlPredicate where) {
        return withConnection(c -> count(c,type,where));
    }
    public <T> boolean exists(Class<T> type, SqlPredicate where) {
        return withConnection(c -> exists(c,type,where));
    }
    private <T> long count(Connection c, Class<T> type, SqlPredicate where) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + mapper(type).table() + " " + SqlAliases.root() +
            (where == null ? "" : " WHERE " + where.sql());
        try (PreparedStatement ps = prepareRead(c,sql)) {
            bindPredicate(ps,where);
            try (ResultSet rs = executeQueryObserved(ps)) {
                if (!rs.next()) throw new SQLException("COUNT returned no row");
                return rs.getLong(1);
            }
        }
    }
    private <T> boolean exists(Connection c, Class<T> type, SqlPredicate where) throws SQLException {
        String sql = "SELECT 1 FROM " + mapper(type).table() + " " + SqlAliases.root() +
            (where == null ? "" : " WHERE " + where.sql()) + " LIMIT 1";
        try (PreparedStatement ps = prepareRead(c,sql)) {
            bindPredicate(ps,where);
            try (ResultSet rs = executeQueryObserved(ps)) { return rs.next(); }
        }
    }
    /** Only meaningful on a live transaction. SELECT ... LIMIT 1 FOR UPDATE/SHARE. */
    public <T> boolean lockOne(Class<T> type, SqlPredicate where, LockMode mode) {
        return withConnection(c -> lockOne(c,type,where,mode),true);
    }
    public <T> List<T> lockRows(Class<T> type,SqlPredicate where,LockMode mode,int limit) {
        return withConnection(c -> lockRows(c,type,where,mode,limit),true);
    }
    /**
     * Lock and return a bounded, primary-key-ordered set of flat entities.
     * The locks remain valid only while the caller's JDBC transaction is open.
     * SKIP LOCKED is for queues, not a consistent snapshot or absence check.
     */
    private <T> List<T> lockRows(Connection c,Class<T> type,SqlPredicate where,
                                 LockMode mode,int limit) throws SQLException {
        Objects.requireNonNull(where,"Row locking requires a predicate");
        Objects.requireNonNull(mode,"mode");
        if(limit<1 || limit>options.maxBufferedRows())
            throw new IllegalArgumentException("lockRows limit must be 1..maxBufferedRows");
        if(c.getAutoCommit())
            throw new IllegalStateException("BJORM row locking requires an active transaction");
        EntityMapper<T> mapper=mapper(type);
        if(mapper.compositeId() || !mapper.children().isEmpty() || !mapper.parents().isEmpty())
            throw new UnsupportedOperationException(
                "lockRows requires a flat mapped entity with a single primary key");
        String sql="SELECT "+mapper.qualifiedColumns(SqlAliases.root())+" FROM "+mapper.table()+" "+SqlAliases.root()+" WHERE "+where.sql()+
            " ORDER BY "+mapper.columnFor(mapper.idProperty())+" LIMIT ? "+mode.sql();
        try(PreparedStatement ps=prepareRead(c,sql)) {
            bindPredicate(ps,where);
            ps.setInt(where.params().size()+1,limit);
            ps.setMaxRows(limit);
            ArrayList<T> rows=new ArrayList<>(Math.min(limit,256));
            try(ResultSet rs=executeQueryObserved(ps)) {
                while(rs.next())rows.add(mapper.read(rs));
            }
            return List.copyOf(rows);
        }
    }
    private <T> boolean lockOne(Connection c, Class<T> type, SqlPredicate where,
                                LockMode mode) throws SQLException {
        Objects.requireNonNull(where, "Row lock requires a predicate");
        Objects.requireNonNull(mode, "mode");
        if (c.getAutoCommit())
            throw new IllegalStateException("BJORM row locking requires an active transaction");
        EntityMapper<T> m=mapper(type);
        String sql = "SELECT " + SqlAliases.root() + "." + m.columnFor(m.idProperty()) + " FROM " + m.table() + " " + SqlAliases.root() +
            " WHERE " + where.sql() + " LIMIT 1 " + mode.sql();
        try (PreparedStatement ps = prepareRead(c,sql)) {
            bindPredicate(ps,where);
            try (ResultSet rs=executeQueryObserved(ps)) { return rs.next(); }
        }
    }
    private void bindPredicate(PreparedStatement ps, SqlPredicate predicate) throws SQLException {
        if (predicate == null) return;
        int i=1;
        for (Object value:predicate.params()) dialect.bindValue(ps,i++,value);
    }
    /** Resolve Spring-style property sorting using generated mapping, never raw client SQL. */
    public <T> SqlOrder mappedOrder(Class<T> type, String property, boolean descending) {
        return new SqlOrder(mapper(type).columnFor(property), descending);
    }
    /** Indexed primary-key seek predicate for cursor pagination, without OFFSET. */
    public <T> SqlPredicate seekAfterId(Class<T> type, Object id, boolean descending) {
        Objects.requireNonNull(id, "afterId");
        EntityMapper<T> m=mapper(type);
        if(m.compositeId())throw new UnsupportedOperationException("Keyset seek needs an explicit tuple predicate for composite IDs");
        return new SqlPredicate(m.columnFor(m.idProperty())+(descending?" < ?":" > ?"),
            java.util.Collections.singletonList(id));
    }
    /** Generated primary-key property names, including all components of a composite key. */
    public <T> List<String> primaryKeyProperties(Class<T> type) {
        return List.copyOf(mapper(type).idProperties());
    }
    public <T> SqlOrder primaryKeyOrder(Class<T> type, boolean descending) {
        EntityMapper<T> m=mapper(type);
        if(m.compositeId())throw new UnsupportedOperationException("Order composite keys explicitly by individual mapped columns");
        return mappedOrder(type,m.idProperty(),descending);
    }
    /** Generated access to @Id; no per-row reflection. */
    public <T> Object primaryKeyValue(T entity) {
        Objects.requireNonNull(entity);
        return mapper(entity.getClass()).id(entity);
    }
    public <T> List<T> list(Select<T> query) {return withConnection(c->list(c,query));}
    public <T> Optional<T> first(Select<T> query) {query.limit(1);return withConnection(c->{if(!mapper(query.type()).children().isEmpty() || !mapper(query.type()).parents().isEmpty()){List<T> values=list(c,query);return values.stream().findFirst();}return first(c,query,null,mapper(query.type()));});}
    public <T> List<Map<String,Object>> fieldRows(Select<T> query,String[] properties) {
        return withConnection(c->{EntityMapper<T> m=mapper(query.type());return project(c,query,properties,rowProperties(m,properties));});
    }
    public <T> Optional<Map<String,Object>> fieldOne(Select<T> query,String[] properties) {
        query.limit(1);
        return withConnection(c->{EntityMapper<T> m=mapper(query.type());return first(c,query,properties,rowProperties(m,properties));});
    }
    public <T,P> List<P> project(Select<T> query,String[] properties,RowMapper<P> projection) {
        return withConnection(c->project(c,query,properties,projection));
    }
    public <T,P> Optional<P> projectOne(Select<T> query,String[] properties,RowMapper<P> projection) {
        query.limit(1);
        return withConnection(c->first(c,query,properties,projection));
    }
    public <T,C> List<C> columnValues(Select<T> query,String property,Class<C> type) {
        return withConnection(c->scalarList(c,query,property,type));
    }
    public <T,C> Optional<C> columnOne(Select<T> query,String property,Class<C> type) {
        query.limit(1);
        return withConnection(c->scalarOne(c,query,property,type));
    }
    public <T> List<T> query(String sql,StatementBinder bind,RowMapper<T> mapper) {requireNativeSqlPermission();return withConnection(c->query(c,sql,bind,mapper));}
    public <T> T one(String sql,StatementBinder bind,RowMapper<T> mapper) {requireNativeSqlPermission();return withConnection(c->one(c,sql,bind,mapper));}
    public int execute(String sql,StatementBinder bind) {requireNativeSqlPermission();return withConnection(c->execute(c,sql,bind));}
    /** Run PostgreSQL DML RETURNING in an owned (or framework-joined) transaction. */
    public <T> List<T> executeReturning(String sql,StatementBinder binder,RowMapper<T> mapper) {
        requireNativeSqlPermission();
        requireReturningDialect();
        return operationTxResult(tx->tx.executeReturning(sql,binder,mapper));
    }
    private void requireReturningDialect() {
        if(!"postgresql".equals(dialect.name()))
            throw new UnsupportedOperationException("Native DML RETURNING is supported only for PostgreSQL");
    }
    public <T> void forEach(Class<T> type,SqlPredicate where,Consumer<T> consumer) {
        forEach(select(type).whereNullable(where),consumer);
    }
    public <T> void forEach(Select<T> query,Consumer<T> consumer) {
        operationTxResult(tx->{tx.forEach(query,consumer);return null;});
    }
    public <T,R> R withStream(Select<T> query,Function<Stream<T>,R> work) {
        Objects.requireNonNull(query,"query");
        Objects.requireNonNull(work,"work");
        return operationTxResult(tx->tx.withStream(query,work));
    }
    public <T> void scan(String sql,StatementBinder binder,RowMapper<T> mapper,Consumer<T> consumer) {
        requireNativeSqlPermission();
        operationTxResult(tx->{tx.scan(sql,binder,mapper,consumer);return null;});
    }
    public <T> void batchInsert(List<T> values) {operationTxResult(tx->{tx.batchInsert(values);return null;});}
    public <T> List<T> batchInsertReturning(List<T> values) {return operationTxResult(tx->tx.batchInsertReturning(values));}
    public <T> void batchUpdate(List<T> values) {operationTxResult(tx->{tx.batchUpdate(values);return null;});}
    public <T> void batchDelete(List<T> values) {operationTxResult(tx->{tx.batchDelete(values);return null;});}
    public <T> List<T> batchUpsertReturning(List<T> values) {return operationTxResult(tx->tx.batchUpsertReturning(values));}
    public <T> List<T> batchUpdateReturning(List<T> values) {return operationTxResult(tx->tx.batchUpdateReturning(values));}
    public <T> T updateReturning(T entity) {return operationTxResult(tx->tx.updateReturning(entity));}
    public <T> int updateBy(Class<T> type,Map<String,?> changes,SqlPredicate predicate) {
        return operationTxResult(tx->tx.updateBy(type,changes,predicate));
    }
    public <T> int deleteBy(Class<T> type,SqlPredicate predicate) {
        return operationTxResult(tx->tx.deleteBy(type,predicate));
    }

    /** Explicit transaction. Use TransactionAwareDataSourceProxy for framework-managed calls, not nested db.tx. */
    public void tx(Consumer<Transaction> work) {txResult(tx->{work.accept(tx);return null;});}
    public <R> R txResult(Function<Transaction,R> work) {
        return transactionResult(work,false,null);
    }
    /**
     * Start a BJORM-owned transaction at an explicitly requested JDBC isolation level.
     * Isolation cannot be changed inside an externally managed Spring transaction.
     * No implicit retries are performed: caller code may have side effects.
     */
    public void tx(TransactionIsolation isolation, Consumer<Transaction> work) {
        Objects.requireNonNull(work,"work");
        txResult(isolation,tx->{work.accept(tx);return null;});
    }
    public <R> R txResult(TransactionIsolation isolation,Function<Transaction,R> work) {
        return transactionResult(work,false,Objects.requireNonNull(isolation,"isolation"));
    }
    /**
     * Explicitly retry the ENTIRE BJORM-owned transaction on serialization
     * conflict (SQLSTATE 40001) or PostgreSQL deadlock (40P01).
     * Only call with idempotent DB work: callbacks are replayed and any
     * external effects (messages, HTTP, files) cannot be rolled back by JDBC.
     * Unlike internal transaction helpers this NEVER joins a Spring transaction.
     */
    public <R> R txResultRetryable(int maxAttempts,Function<Transaction,R> work) {
        return retryTransaction(null,maxAttempts,work);
    }
    public <R> R txResultRetryable(TransactionIsolation isolation,int maxAttempts,
                                   Function<Transaction,R> work) {
        return retryTransaction(Objects.requireNonNull(isolation,"isolation"),maxAttempts,work);
    }
    private <R> R retryTransaction(TransactionIsolation isolation,int maxAttempts,
                                    Function<Transaction,R> work) {
        Objects.requireNonNull(work,"work");
        if(maxAttempts<1 || maxAttempts>8)
            throw new IllegalArgumentException("maxAttempts must be between 1 and 8");
        for(int attempt=1;;attempt++) {
            try {return transactionResult(work,false,isolation);}
            catch(BjormException failure) {
                if(attempt>=maxAttempts || !retryableSqlFailure(failure))throw failure;
                // transactionResult has already rolled back and closed this attempt.
            }
        }
    }
    private static boolean retryableSqlFailure(Throwable failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause())
            if(cause instanceof SQLException sql) {
                String state=sql.getSQLState();
                return "40001".equals(state) || "40P01".equals(state);
            }
        return false;
    }
    /** Internal multi-statement operations may join a framework-owned transaction.
     * Explicit db.tx/txResult still refuse nesting. */
    private <R> R operationTxResult(Function<Transaction,R> work) {
        return transactionResult(work,true,null);
    }
    private <R> R transactionResult(Function<Transaction,R> work,boolean joinExisting,
                                     TransactionIsolation isolation) {
        Objects.requireNonNull(work);
        requireTenant();
        try(Connection c=dataSource.getConnection()) {
            if(tenantRequired && !postgresTenant() && !mysqlTenant())
                throw new UnsupportedOperationException("Tenant isolation not supported by dialect: "+dialect.name());
            String previousCatalog=mysqlTenant()?c.getCatalog():null;
            if(mysqlTenant() && (previousCatalog==null || previousCatalog.isBlank()))
                throw new IllegalStateException("Cannot restore MySQL catalog: connection has no original catalog");
            try {
                if(mysqlTenant()) {
                    c.setCatalog(tenantSchema);
                    if(!tenantSchema.equals(c.getCatalog()))
                        throw new IllegalStateException("JDBC driver did not activate the tenant catalog");
                }
            boolean previous=c.getAutoCommit();
            if(!previous && !joinExisting)
                throw new IllegalStateException("Connection already transactional; do not nest Bjorm.tx in another transaction");
            if(isolation!=null && !previous)
                throw new IllegalStateException("Cannot override isolation in an existing transaction");
            int previousIsolation=isolation==null?-1:c.getTransactionIsolation();
            boolean isolationChanged=isolation!=null && previousIsolation!=isolation.jdbcLevel();
            try {
            if(isolationChanged)c.setTransactionIsolation(isolation.jdbcLevel());
            if(previous)c.setAutoCommit(false);
            Transaction tx=new Transaction(c);
            Throwable failure=null;
            try {
                if(postgresTenant())applyPostgresTenant(c);
                R result=work.apply(tx);
                if(previous)c.commit();
                return result;
            } catch(RuntimeException|Error e) {
                failure=e;
                if(previous)try {c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}
                throw e;
            } catch(SQLException e) {
                failure=e;
                if(previous)try {c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}
                throw new BjormException("Commit failed",e);
            } finally {
                tx.active=false;
                tx.identityMap.clear();
                try {if(previous)c.setAutoCommit(true);} catch(SQLException restore) {
                    if(failure!=null)failure.addSuppressed(restore);
                    else throw new BjormException("Could not restore connection state",restore);
                }
            }
            } finally {
                if(isolationChanged)c.setTransactionIsolation(previousIsolation);
            }
            } finally {
                if(mysqlTenant())c.setCatalog(previousCatalog);
            }
        } catch(SQLException e) {throw new BjormException("Transaction failed",e);}
    }
    private <T> T insert(Connection c,T entity) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        T prepared=m.materializeInsert(entity);
        try(PreparedStatement ps=m.generatedId()?prepare(c,m.insertSql(dialect),new String[]{m.columnFor(m.idProperty())}):prepare(c,m.insertSql(dialect))) {
            m.bindInsert(ps,prepared,dialect);
            if(executeUpdateObserved(ps)!=1)throw new SQLException("Insert affected unexpected number of rows");
            if(m.generatedId())try(ResultSet keys=ps.getGeneratedKeys()) {
                if(!keys.next())throw new SQLException("No generated key returned for "+m.type().getName());
                return m.withGeneratedId(keys,prepared);
            }
        }
        return prepared;
    }
    private <T> int upsert(Connection c,T entity) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        if(m.generatedId()) throw new IllegalArgumentException("Upsert requires an application-assigned primary key");
        return upsertPrepared(c,m.materializeUpsert(entity));
    }
    /** Accepts an already materialized entity; never invokes user preparation twice. */
    private <T> int upsertPrepared(Connection c,T entity) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        if(m.generatedId()) throw new IllegalArgumentException("Upsert requires an application-assigned primary key");
        if(m.compositeId())throw new UnsupportedOperationException("Composite-key UPSERT is not supported; use explicit INSERT or optimistic UPDATE");
        if(m.id(entity)==null) throw new IllegalArgumentException("Upsert requires non-null ID");
        // A MySQL AUTO_INCREMENT key may silently change on UPDATE id=NULL.
        // PK-only conflict checks are consequently safe only for textual/UUID IDs.
        if("mysql".equals(dialect.name()) && !(m.id(entity) instanceof UUID || m.id(entity) instanceof String))
            throw new UnsupportedOperationException("MySQL UPSERT requires UUID/String primary key; use insert/update for numeric IDs");
        if("mysql".equals(dialect.name()) && m.optimisticLocking())
            return mysqlVersionedUpsert(c,m,entity);
        try(PreparedStatement ps=prepare(c,m.upsertSql(dialect))) {
            m.bindUpsert(ps,entity,dialect);
            int changed=executeUpdateObserved(ps);
            if(m.optimisticLocking() && changed==0) throw new OptimisticLockException("Stale upsert: "+m.type().getName());
            return changed;
        }
    }
    /**
     * MySQL 8.4 performs compare-and-swap inside one atomic INSERT ... ON
     * DUPLICATE KEY UPDATE. The generated dialect SQL rejects stale version
     * and secondary-UNIQUE collisions by assigning NULL to a NOT NULL PK,
     * so the JDBC statement itself never commits an unrelated update.
     *
     * A locking lookup is performed only on error code 1048 (NOT NULL
     * violation) to distinguish stale versions from secondary-key conflicts.
     * It executes inside the caller-owned transaction; the SQL exception is
     * otherwise preserved for the application to handle.
     */
    private <T> int mysqlVersionedUpsert(Connection c,EntityMapper<T> mapper,T prepared) throws SQLException {
        if(c.getAutoCommit())
            throw new IllegalStateException("MySQL versioned UPSERT must execute inside a transaction");
        if(mapper.versionColumn()==null)
            throw new IllegalArgumentException("Versioned entity mapper has no generated version metadata");
        try(PreparedStatement ps=prepare(c,mapper.upsertSql(dialect))) {
            mapper.bindUpsert(ps,prepared,dialect);
            int affected=executeUpdateObserved(ps);
            // MySQL returns 1 for insert, 2 for changed UPDATE.
            if(affected==1 || affected==2)return affected;
            if(affected==0)
                throw new OptimisticLockException("Stale upsert: "+mapper.type().getName());
            throw new SQLException("Unexpected MySQL UPSERT affected row count: "+affected);
        } catch(SQLException failure) {
            // 1048 / 23000 is the deliberately invalid NULL PK in the CAS
            // guard. Other SQL errors (including deadlocks) are not rewritten.
            if(failure.getErrorCode()!=1048 || !"23000".equals(failure.getSQLState()))
                throw failure;
            String sql="SELECT "+mapper.versionColumn()+" FROM "+mapper.table()+
                " WHERE "+mapper.columnFor(mapper.idProperty())+" = ? FOR UPDATE";
            try(PreparedStatement ps=prepareRead(c,sql)) {
                mapper.bindId(ps,1,mapper.id(prepared),dialect);
                try(ResultSet rs=executeQueryObserved(ps)) {
                    if(rs.next() && !Objects.equals(rs.getObject(1),mapper.versionValue(prepared)))
                        throw new OptimisticLockException("Stale upsert: "+mapper.type().getName());
                }
            }
            throw failure;
        }
    }
    private <T> T persistGraph(Connection c,T entity,boolean upserting,IdentityHashMap<Object,Boolean> visited) throws SQLException {
        if(visited.put(entity,Boolean.TRUE)!=null) throw new IllegalArgumentException("Cycle or repeated entity in persistence graph");
        if(upserting) {entity=this.<T>mapper(entity.getClass()).materializeUpsert(entity);upsertPrepared(c,entity);}
        else entity=insert(c,entity);
        EntityMapper<T> m=mapper(entity.getClass());
        Object id=m.id(entity);
        if(id==null && !m.children().isEmpty()) throw new IllegalArgumentException("Parent ID is null: "+m.type());
        for(ChildRelation<T> relation:m.children()) {
            Iterable<?> children=relation.children(entity);
            if(children==null)continue;
            List<Object> persisted=new ArrayList<>();
            for(Object child:children) {
                Objects.requireNonNull(child,"Child cannot be null");
                if(!relation.childType().isInstance(child)) throw new IllegalArgumentException("Unexpected child type: "+child.getClass());
                Object attached=relation.attached(id,child);
                persisted.add(persistGraph(c,attached,upserting,visited));
            }
            entity=m.withChildren(entity,relation.property(),persisted);
        }
        return entity;
    }
    private record DeleteKey(Class<?> type,Object id) {}
    private <T> int removeGraph(Connection c,T entity,Set<DeleteKey> visited) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        Object id=Objects.requireNonNull(m.id(entity),"Cannot delete entity with null ID");
        if(!visited.add(new DeleteKey(m.type(),id)))throw new IllegalArgumentException("Cycle in persisted entity graph");
        for(ChildRelation<T> relation:m.children()) {
            EntityMapper<Object> childMapper=mapper(relation.childType());
            String fk=childMapper.columnFor(relation.mappedBy());
            if(childMapper.children().isEmpty() && !childMapper.optimisticLocking()) {
                try(PreparedStatement ps=prepare(c,"DELETE FROM "+childMapper.table()+" WHERE "+fk+" = ?")) {
                    childMapper.bindId(ps,1,id,dialect);
                    executeUpdateObserved(ps);
                }
            } else {
                // Descendants must be removed before the child: do not depend on loaded collections.
                try(PreparedStatement ps=prepareRead(c,"SELECT "+childMapper.qualifiedColumns(SqlAliases.root())+" FROM "+childMapper.table()+" "+SqlAliases.root()+" WHERE "+SqlAliases.root()+"."+fk+" = ?")) {
                    childMapper.bindId(ps,1,id,dialect);
                    try(ResultSet rs=executeQueryObserved(ps)) {
                        while(rs.next())removeGraph(c,childMapper.read(rs),visited);
                    }
                }
            }
        }
        return delete(c,entity);
    }
    /** Strong guard against accidental whole-table writes and bypassed optimistic locking. */
    private <T> void requireBulkSafe(Class<T> type,SqlPredicate predicate) {
        Objects.requireNonNull(predicate,"A typed WHERE predicate is required for bulk mutation");
        if(predicate.sql().isBlank())throw new IllegalArgumentException("Empty WHERE predicate");
        EntityMapper<T> m=mapper(type);
        if(m.optimisticLocking())
            throw new IllegalArgumentException("Bulk mutation bypasses @Version; use batchUpdate/batchDelete");
        if(!m.children().isEmpty())
            throw new IllegalArgumentException("Bulk mutation bypasses entity graph semantics; use batch operations");
    }
    private <T> int updateBy(Connection c,Class<T> type,Map<String,?> changes,SqlPredicate predicate) throws SQLException {
        requireBulkSafe(type,predicate);
        Objects.requireNonNull(changes,"changes");
        if(changes.isEmpty())throw new IllegalArgumentException("At least one changed property required");
        EntityMapper<T> m=mapper(type);
        Map<String,Object> ordered=new TreeMap<>();
        for(var entry:changes.entrySet())
            ordered.put(Objects.requireNonNull(entry.getKey(),"property"),entry.getValue());
        if(m.updatedAtProperty()!=null) {
            if(ordered.containsKey(m.updatedAtProperty()))
                throw new IllegalArgumentException("@UpdatedAt is maintained by BJORM");
            ordered.put(m.updatedAtProperty(),m.updatedAtNow());
        }
        StringJoiner assignments=new StringJoiner(", ");
        List<Object> values=new ArrayList<>(ordered.size());
        for(var entry:ordered.entrySet()) {
            String property=Objects.requireNonNull(entry.getKey(),"property");
            if(m.isIdProperty(property)) throw new IllegalArgumentException("Bulk mutation cannot alter @Id");
            String column=m.columnFor(property); // generated mapping; rejects unknown input
            assignments.add(column+" = "+m.bulkParameterSql(property,dialect));
            values.add(m.bulkParameterValue(property,entry.getValue()));
        }
        String sql="UPDATE "+m.table()+" "+SqlAliases.root()+" SET "+assignments+" WHERE "+predicate.sql();
        try(PreparedStatement ps=prepare(c,sql)) {
            int i=1;
            for(Object value:values)dialect.bindValue(ps,i++,value);
            for(Object value:predicate.params())dialect.bindValue(ps,i++,value);
            return executeUpdateObserved(ps);
        }
    }
    private <T> int deleteBy(Connection c,Class<T> type,SqlPredicate predicate) throws SQLException {
        requireBulkSafe(type,predicate);
        EntityMapper<T> m=mapper(type);
        try(PreparedStatement ps=prepare(c,"DELETE FROM "+m.table()+" "+SqlAliases.root()+" WHERE "+predicate.sql())) {
            bindPredicate(ps,predicate);
            return executeUpdateObserved(ps);
        }
    }
    private <T> int update(Connection c,T entity) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        try(PreparedStatement ps=prepare(c,m.updateSql(dialect))) {
            m.bindUpdate(ps,m.materializeUpdate(entity),dialect);
            int count=executeUpdateObserved(ps);
            if(m.optimisticLocking() && count==0)throw new OptimisticLockException("Stale update: " + m.type().getName());
            return count;
        }
    }
    private <T> int delete(Connection c,T entity) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        try(PreparedStatement ps=prepare(c,m.deleteSql(dialect))) {
            m.bindDelete(ps,entity,dialect);
            int count=executeUpdateObserved(ps);
            if(m.optimisticLocking() && count==0)throw new OptimisticLockException("Stale delete: " + m.type().getName());
            return count;
        }
    }
    private <T> T find(Connection c,Class<T> type,Object id) throws SQLException {
        EntityMapper<T> m=mapper(type);
        if (!m.children().isEmpty() || !m.parents().isEmpty()) {
            List<T> values = list(c,new Select<>(this,type).where(new SqlPredicate(
                m.columnFor(m.idProperty())+" = ?",Collections.singletonList(id))).limit(1));
            return values.isEmpty() ? null : values.getFirst();
        }
        try(PreparedStatement ps=prepareRead(c,m.selectSql(dialect))) {
            m.bindId(ps,1,id,dialect);
            try(ResultSet rs=executeQueryObserved(ps)) {return rs.next()?m.read(rs):null;}
        }
    }
    private <T> List<T> list(Connection c,Select<T> query) throws SQLException {
        EntityMapper<T> m=mapper(query.type());
        if (!m.children().isEmpty() || !m.parents().isEmpty()) {
            ArrayList<T> rows=new ArrayList<>();
            if(!m.children().isEmpty())readGraph(c,query,rows::add,true);
            else readParents(c,query,rows::add,true);
            return rows;
        }
        try(PreparedStatement ps=prepareRead(c,query.sql(m,type->mapper(type),dialect))) {
            boundRows(ps);
            query.bind(ps,dialect);
            try(ResultSet rs=executeQueryObserved(ps)) {
                ArrayList<T> result=new ArrayList<>();
                while(rs.next()){
                    if(query.hasRightOrFullJoin() && rs.getObject(m.idColumnIndex())==null)continue;
                    checkRows(result.size()+1);result.add(m.read(rs));
                }
                return result;
            }
        }
    }
    /** Composite to-one JOINs use the exact ordered target key, not the first @Id. */
    private static String parentJoin(EntityMapper<?> source,String sourceAlias,
                                     EntityMapper<?> target,String targetAlias,
                                     ParentRelation<?> relation) {
        List<String> foreignKeys=relation.mappedByParts();
        List<String> targetKeys=target.idProperties();
        if(foreignKeys.isEmpty() || foreignKeys.size()!=targetKeys.size())
            throw new IllegalArgumentException("Foreign-key parts do not match target composite key");
        StringJoiner predicates=new StringJoiner(" AND ");
        for(int i=0;i<foreignKeys.size();i++)
            predicates.add(sourceAlias+"."+source.columnFor(foreignKeys.get(i))+
                " = "+targetAlias+"."+target.columnFor(targetKeys.get(i)));
        return predicates.toString();
    }
    /** A compile-time mapped to-one path, ordered parents before descendants. */
    private record ParentHop(int parentSlot,ParentRelation<Object> relation,
                             EntityMapper<Object> target,int offset,String alias) {}

    /** Build a finite single-query join tree. Never follow to-many targets or type cycles. */
    @SuppressWarnings("unchecked")
    private void collectParentHops(EntityMapper<?> source,int parentSlot,
                                   Set<Class<?>> ancestors,List<ParentHop> hops,
                                   int[] nextOffset) {
        if(!ancestors.add(source.type()))
            throw new UnsupportedOperationException(
                "Recursive @ManyToOne type cycle requires an explicit query: "+source.type());
        if(ancestors.size()>8)
            throw new UnsupportedOperationException("Eager @ManyToOne path exceeds eight types");
        for(ParentRelation<?> relationship:source.parents()) {
            if(hops.size()>=64)
                throw new UnsupportedOperationException("Eager to-one graph exceeds 64 mapped relation slots");
            EntityMapper<Object> target=mapper(relationship.targetType());
            if(!target.children().isEmpty())
                throw new UnsupportedOperationException(
                    "Referenced to-many graphs require a separate explicit query: "+target.type());
            // A nested INNER JOIN after a nullable ancestor would wrongly filter
            // away the root. Until grouped joins are available, fail explicitly.
            if(parentSlot>=0 && relationship.joinType()!=JoinType.LEFT)
                throw new UnsupportedOperationException(
                    "Nested @ManyToOne requires LEFT join to preserve nullable ancestors");
            int slotIndex=hops.size();
            ParentHop hop=new ParentHop(parentSlot,(ParentRelation<Object>)relationship,
                target,nextOffset[0],SqlAliases.at(slotIndex+1));
            hops.add(hop);
            nextOffset[0]+=target.columnCount();
            collectParentHops(target,slotIndex,ancestors,hops,nextOffset);
        }
        ancestors.remove(source.type());
    }

    /**
     * Eager multi-hop to-one relations in one SQL statement. A CTE pages root
     * rows before joining referenced entities. Every JOIN is key-based and
     * every mapper is compiler-generated; no N+1 or runtime entity reflection.
     */
    private <T> void readParents(Connection c,Select<T> query,
            Consumer<T> emit,boolean bounded) throws SQLException {
        Objects.requireNonNull(emit,"emit");
        EntityMapper<T> root=mapper(query.type());
        if(query.hasJoins())
            throw new IllegalArgumentException(
                "Automatic @ManyToOne loading cannot be combined with explicit query JOINs");
        if(root.parents().isEmpty())
            throw new IllegalArgumentException("No eager to-one relations registered");
        ArrayList<ParentHop> hops=new ArrayList<>();
        collectParentHops(root,-1,new HashSet<>(),hops,
            new int[]{root.columnCount()+1});
        // Index zero contains root references. Index i+1 contains relations
        // directly attached to hop i, precomputed once rather than per row.
        ArrayList<List<Integer>> children=new ArrayList<>(hops.size()+1);
        for(int i=0;i<=hops.size();i++)children.add(new ArrayList<>());
        for(int i=0;i<hops.size();i++)
            children.get(hops.get(i).parentSlot()+1).add(i);

        String rootSql=query.sql(root,type->mapper(type),dialect);
        StringBuilder sql=new StringBuilder("WITH roots AS (").append(rootSql)
            .append(") SELECT ").append(root.qualifiedColumns(SqlAliases.root()));
        for(ParentHop hop:hops)
            sql.append(", ").append(hop.target().qualifiedColumns(hop.alias()));
        sql.append(" FROM roots ").append(SqlAliases.root());
        for(ParentHop hop:hops) {
            EntityMapper<?> source=hop.parentSlot()<0
                ?root:hops.get(hop.parentSlot()).target();
            String sourceAlias=hop.parentSlot()<0
                ?SqlAliases.root():hops.get(hop.parentSlot()).alias();
            sql.append(' ').append(dialect.join(hop.relation().joinType())).append(' ')
               .append(hop.target().table()).append(' ').append(hop.alias())
               .append(" ON ").append(parentJoin(source,sourceAlias,hop.target(),
                   hop.alias(),hop.relation()));
        }
        boolean firstOrder=true;
        for(SqlOrder order:query.sorting()) {
            String field=order.column();
            int separator=field.lastIndexOf('.');
            if(separator>=0 && !field.substring(0,separator).equals(query.rootAlias()))
                throw new IllegalArgumentException("@ManyToOne ORDER BY supports only root columns: "+field);
            String column=separator<0?field:field.substring(separator+1);
            root.columnIndex(column);
            sql.append(firstOrder?" ORDER BY ":", ")
               .append(SqlAliases.root()).append(".").append(column).append(order.descending()?" DESC":" ASC");
            firstOrder=false;
        }
        try(PreparedStatement ps=prepareRead(c,sql.toString())) {
            if(bounded)boundRows(ps);
            query.bind(ps,dialect);
            try(ResultSet rs=executeQueryObserved(ps)) {
                int count=0;
                while(rs.next()) {
                    if(bounded)checkRows(++count);
                    if(rs.getObject(root.idColumnIndex())==null)continue;
                    Object[] values=new Object[hops.size()];
                    for(int i=hops.size()-1;i>=0;i--) {
                        ParentHop hop=hops.get(i);
                        Object joinedId=rs.getObject(hop.offset()+hop.target().idColumnIndex()-1);
                        Object value=joinedId==null?null:hop.target().readAt(rs,hop.offset());
                        if(value!=null)
                            for(int childSlot:children.get(i+1))
                                value=hop.target().withParent(value,
                                    hops.get(childSlot).relation().property(),values[childSlot]);
                        values[i]=value;
                    }
                    T value=root.readAt(rs,1);
                    for(int rootSlot:children.getFirst())
                        value=root.withParent(value,hops.get(rootSlot).relation().property(),
                            values[rootSlot]);
                    emit.accept(value);
                }
            }
        }
    }
    /**
     * Eager entity graphs: one SQL statement. Multiple sibling collections are
     * UNION ALL branches (not simultaneous sibling JOINs, which multiply rows).
     * LIMIT/OFFSET apply inside the root CTE, BEFORE expanding child rows.
     */
    private record GraphSlot(EntityMapper<Object> mapper,ChildRelation<Object> relation,
                             int parent,int offset,String alias) {}
    /** Additional to-one lookup attached to a child slot along the active UNION path. */
    private record GraphParentSlot(int childSlot,ParentRelation<Object> relation,
                                   EntityMapper<Object> target,int offset,String alias) {}
    private static final class GraphNode {
        final EntityMapper<Object> mapper;
        Object value;
        final Map<Integer,LinkedHashMap<Object,GraphNode>> children=new HashMap<>();
        GraphNode(EntityMapper<Object> mapper,Object value){this.mapper=mapper;this.value=value;}
    }
    @SuppressWarnings("unchecked")
    private <T> void readGraph(Connection c,Select<T> query,Consumer<T> emit,boolean bounded) throws SQLException {
        EntityMapper<T> root=mapper(query.type());
        ArrayList<GraphSlot> slots=new ArrayList<>();
        ArrayList<List<Integer>> paths=new ArrayList<>();
        collectGraphPaths((EntityMapper<Object>)root,0,new ArrayList<>(),new HashSet<>(),slots,paths);
        // Joined-result hydration groups rows by root; ordering on a joined-only field
        // cannot be represented by the outer UNION ALL query.
        for(SqlOrder order:query.sorting()) {
            String field=order.column();
            int separator=field.lastIndexOf('.');
            if(separator>=0 && !field.substring(0,separator).equals(query.rootAlias()))
                throw new IllegalArgumentException("Eager graph ORDER BY supports only root columns: "+field);
            String column=separator>=0?field.substring(separator+1):field;
            try {root.columnIndex(column);}
            catch(IllegalArgumentException e) {
                throw new IllegalArgumentException("Eager graph ORDER BY supports only mapped root columns: "+field,e);
            }
        }
        String rootSql=query.sql(root,type->mapper(type),dialect);
        // Multiple matching explicit joins cannot multiply roots before eager expansion.
        // LIMIT/OFFSET on such joins remains rejected by Select.sql().
        if(query.hasJoins())rootSql="SELECT DISTINCT * FROM ("+rootSql+") "+SqlAliases.root();
        int rootColumns=root.columnCount();
        // Append root @ManyToOne columns AFTER all child slots so existing
        // discriminator and child slot offsets are preserved across UNION paths.
        List<ParentRelation<T>> parents=root.parents();
        ArrayList<EntityMapper<Object>> parentTargets=new ArrayList<>(parents.size());
        ArrayList<Integer> parentOffsets=new ArrayList<>(parents.size());
        int parentOffset=rootColumns+2;
        for(GraphSlot slot:slots)parentOffset+=slot.mapper.columnCount();
        for(ParentRelation<T> relation:parents) {
            EntityMapper<Object> target=mapper(relation.targetType());
            if(!target.children().isEmpty() || !target.parents().isEmpty())
                throw new UnsupportedOperationException("Nested @ManyToOne target graphs are unsupported");
            parentTargets.add(target);
            parentOffsets.add(parentOffset);
            parentOffset+=target.columnCount();
        }
        // A child may itself have @ManyToOne references. Append their joined
        // columns after root references; every UNION branch must keep exactly
        // the same projected column layout, even if it does not traverse the child.
        ArrayList<GraphParentSlot> nestedParents=new ArrayList<>();
        for(int childIndex=0;childIndex<slots.size();childIndex++) {
            EntityMapper<Object> child=slots.get(childIndex).mapper();
            for(ParentRelation<Object> relation:child.parents()) {
                EntityMapper<Object> target=mapper(relation.targetType());
                if(!target.children().isEmpty() || !target.parents().isEmpty())
                    throw new UnsupportedOperationException(
                        "Nested child @ManyToOne target must be a flat entity");
                nestedParents.add(new GraphParentSlot(childIndex,relation,target,parentOffset,
                    SqlAliases.at(slots.size()+parents.size()+nestedParents.size()+1)));
                parentOffset+=target.columnCount();
            }
        }
        StringBuilder sql=new StringBuilder("WITH roots AS (").append(rootSql).append(") ");
        for(int branch=0;branch<paths.size();branch++) {
            if(branch>0)sql.append(" UNION ALL ");
            List<Integer> path=paths.get(branch);
            sql.append("SELECT ").append(root.qualifiedColumns(SqlAliases.root())).append(", ").append(branch);
            for(int i=0;i<slots.size();i++) {
                GraphSlot slot=slots.get(i);
                if(path.contains(i)) sql.append(", ").append(slot.mapper.qualifiedColumns(slot.alias));
                else for(int col=0;col<slot.mapper.columnCount();col++)sql.append(", NULL");
            }
            for(int i=0;i<parentTargets.size();i++)
                sql.append(", ").append(parentTargets.get(i).qualifiedColumns(SqlAliases.at(slots.size()+i+1)));
            for(GraphParentSlot nested:nestedParents) {
                if(path.contains(nested.childSlot()))
                    sql.append(", ").append(nested.target().qualifiedColumns(nested.alias()));
                else for(int col=0;col<nested.target().columnCount();col++)
                    sql.append(", NULL");
            }
            sql.append(" FROM roots ").append(SqlAliases.root());
            for(int i:path) {
                GraphSlot slot=slots.get(i);
                EntityMapper<Object> parent=slot.parent==0?(EntityMapper<Object>)root:slots.get(slot.parent-1).mapper;
                String parentAlias=slot.parent==0?SqlAliases.root():slots.get(slot.parent-1).alias;
                sql.append(' ').append(dialect.join(slot.relation.joinType())).append(' ').append(slot.mapper.table()).append(' ').append(slot.alias)
                   .append(" ON ").append(slot.alias).append('.').append(slot.mapper.columnFor(slot.relation.mappedBy()))
                   .append(" = ").append(parentAlias).append('.').append(parent.columnFor(parent.idProperty()));
            }
            for(int i=0;i<parents.size();i++) {
                ParentRelation<T> relation=parents.get(i);
                EntityMapper<Object> target=parentTargets.get(i);
                sql.append(' ').append(dialect.join(relation.joinType())).append(' ')
                   .append(target.table()).append(" ").append(SqlAliases.at(slots.size()+i+1))
                   .append(" ON ").append(parentJoin(root,SqlAliases.root(),target,SqlAliases.at(slots.size()+i+1),relation));
            }
            for(GraphParentSlot nested:nestedParents) {
                if(!path.contains(nested.childSlot()))continue;
                GraphSlot child=slots.get(nested.childSlot());
                sql.append(' ').append(dialect.join(nested.relation().joinType())).append(' ')
                   .append(nested.target().table()).append(' ').append(nested.alias())
                   .append(" ON ").append(parentJoin(child.mapper(),child.alias(),
                       nested.target(),nested.alias(),nested.relation()));
            }
        }
        // Keep all rows belonging to a root contiguous so one root at a time can be emitted.
        for(SqlOrder order:query.sorting()) {
            String column=order.column();
            int dot=column.lastIndexOf('.');
            if(dot>=0)column=column.substring(dot+1);
            sql.append(query.sorting().getFirst()==order?" ORDER BY ":", ")
               .append(root.columnIndex(column)).append(order.descending()?" DESC":" ASC");
        }
        sql.append(query.sorting().isEmpty()?" ORDER BY ":", ").append(root.idColumnIndex());
        try(PreparedStatement ps=prepareRead(c,sql.toString())) {
            if(bounded)boundRows(ps);
            query.bind(ps,dialect);
            try(ResultSet rs=executeQueryObserved(ps)) {
                Object lastId=null;
                GraphNode current=null;
                int fetchedRows=0;
                int groupRows=0;
                while(rs.next()) {
                    Object id=rs.getObject(root.idColumnIndex());
                    // RIGHT/FULL outer rows without a root cannot materialize a parent entity.
                    if(id==null)continue;
                    if(current==null || !Objects.equals(lastId,id)) {
                        if(current!=null)emit.accept((T)finishGraph(current,slots,0));
                        T value=root.read(rs);
                        for(int i=0;i<parents.size();i++) {
                            EntityMapper<Object> target=parentTargets.get(i);
                            int parentStart=parentOffsets.get(i);
                            Object targetId=rs.getObject(parentStart+target.idColumnIndex()-1);
                            Object parent=targetId==null?null:target.readAt(rs,parentStart);
                            value=root.withParent(value,parents.get(i).property(),parent);
                        }
                        current=new GraphNode((EntityMapper<Object>)root,value);
                        lastId=id;
                        groupRows=0;
                    }
                    checkRows(++groupRows); // one root may still have an enormous collection
                    ++fetchedRows;
                    if(bounded)checkRows(fetchedRows);
                    List<Integer> path=paths.get(rs.getInt(rootColumns+1));
                    GraphNode cursor=current;
                    for(int slotIndex:path) {
                        GraphSlot slot=slots.get(slotIndex);
                        Object childId=rs.getObject(slot.offset+slot.mapper.idColumnIndex()-1);
                        if(childId==null)break;
                        LinkedHashMap<Object,GraphNode> byId=cursor.children.computeIfAbsent(slotIndex,k->new LinkedHashMap<>());
                        GraphNode child=byId.get(childId);
                        if(child==null) {
                            Object childValue=slot.mapper.readAt(rs,slot.offset);
                            for(GraphParentSlot nested:nestedParents) {
                                if(nested.childSlot()!=slotIndex)continue;
                                Object joinedId=rs.getObject(nested.offset()+
                                    nested.target().idColumnIndex()-1);
                                Object referenced=joinedId==null?null:
                                    nested.target().readAt(rs,nested.offset());
                                childValue=slot.mapper.withParent(childValue,
                                    nested.relation().property(),referenced);
                            }
                            child=new GraphNode(slot.mapper,childValue);
                            byId.put(childId,child);
                        }
                        cursor=child;
                    }
                }
                if(current!=null)emit.accept((T)finishGraph(current,slots,0));
            }
        }
    }
    private Object finishGraph(GraphNode node,List<GraphSlot> slots,int parentSlot) {
        Object value=node.value;
        // Attach all mapped child relations, including empty collections on LEFT JOIN misses.
        for(ChildRelation<Object> relation:node.mapper.children()) {
            int slotIndex=-1;
            for(int i=0;i<slots.size();i++)if(slots.get(i).parent()==parentSlot && slots.get(i).relation()==relation){slotIndex=i;break;}
            if(slotIndex<0)throw new IllegalStateException("Missing compiled child graph slot");
            LinkedHashMap<Object,GraphNode> related=node.children.get(slotIndex);
            ArrayList<Object> values=new ArrayList<>(related==null?0:related.size());
            if(related!=null)for(GraphNode child:related.values())values.add(finishGraph(child,slots,slotIndex+1));
            value=node.mapper.withChildren(value,relation.property(),values);
        }
        return value;
    }
    @SuppressWarnings("unchecked")
    private void collectGraphPaths(EntityMapper<Object> parent,int parentSlot,List<Integer> path,
                                   Set<Class<?>> ancestors,List<GraphSlot> slots,List<List<Integer>> paths) {
        if(!ancestors.add(parent.type()))throw new IllegalArgumentException(
            "Recursive @Children type cycle not supported for one-query graph loading: "+parent.type());
        if(parent.children().isEmpty())paths.add(List.copyOf(path));
        for(ChildRelation<Object> relation:parent.children()) {
            EntityMapper<Object> child=(EntityMapper<Object>)mapper(relation.childType());
            int index=slots.size();
            int offset=2; // root count is added after constructing all slots below
            slots.add(new GraphSlot(child,relation,parentSlot,offset,SqlAliases.at(index+1)));
            ArrayList<Integer> next=new ArrayList<>(path);next.add(index);
            collectGraphPaths(child,index+1,next,ancestors,slots,paths);
        }
        ancestors.remove(parent.type());
        // set physical SQL field offsets once, after all slots are known (called by top level)
        if(parentSlot==0) {
            int offset=mapper(parent.type()).columnCount()+2;
            for(int i=0;i<slots.size();i++) {
                GraphSlot slot=slots.get(i);
                slots.set(i,new GraphSlot(slot.mapper,slot.relation,slot.parent,offset,slot.alias));
                offset+=slot.mapper.columnCount();
            }
        }
    }
    private static <T> RowMapper<Map<String,Object>> rowProperties(EntityMapper<T> mapper,String[] properties) {
        return rs -> {
            Map<String,Object> row=new LinkedHashMap<>(properties.length);
            for(int i=0;i<properties.length;i++)row.put(properties[i],mapper.readProperty(rs,i+1,properties[i]));
            return row;
        };
    }
    private static Class<?> boxedType(Class<?> type) {
        Objects.requireNonNull(type,"type");
        if(!type.isPrimitive())return type;
        if(type==int.class)return Integer.class;
        if(type==long.class)return Long.class;
        if(type==boolean.class)return Boolean.class;
        if(type==short.class)return Short.class;
        if(type==byte.class)return Byte.class;
        if(type==float.class)return Float.class;
        if(type==double.class)return Double.class;
        if(type==char.class)return Character.class;
        throw new IllegalArgumentException("Unsupported scalar type: "+type);
    }
    @SuppressWarnings("unchecked")
    private static <C> C scalarCast(Class<C> type,Object value) {
        return (C)boxedType(type).cast(value);
    }
    private <T,C> List<C> scalarList(Connection c,Select<T> query,String property,Class<C> type) throws SQLException {
        EntityMapper<T> m=mapper(query.type());
        return project(c,query,new String[]{property},rs->scalarCast(type,m.readProperty(rs,1,property)));
    }
    private <T,C> Optional<C> scalarOne(Connection c,Select<T> query,String property,Class<C> type) throws SQLException {
        EntityMapper<T> m=mapper(query.type());
        return first(c,query,new String[]{property},rs->scalarCast(type,m.readProperty(rs,1,property)));
    }
    private <T,P> List<P> project(Connection c,Select<T> query,String[] properties,RowMapper<P> projection) throws SQLException {
        Objects.requireNonNull(projection);
        EntityMapper<T> m=mapper(query.type());
        String sql=query.sql(m,type->mapper(type),properties,dialect);
        try(PreparedStatement ps=prepareRead(c,sql)) {
            boundRows(ps);
            query.bind(ps,dialect);
            try(ResultSet rs=executeQueryObserved(ps)){
                ArrayList<P> rows=new ArrayList<>();
                while(rs.next()){checkRows(rows.size()+1);rows.add(projection.read(rs));}
                return rows;
            }
        }
    }
    private <T,P> Optional<P> first(Connection c,Select<T> query,String[] properties,RowMapper<P> projection) throws SQLException {
        Objects.requireNonNull(projection);
        EntityMapper<T> m=mapper(query.type());
        String sql=query.sql(m,type->mapper(type),properties,dialect);
        try(PreparedStatement ps=prepareRead(c,sql)) {
            query.bind(ps,dialect);
            try(ResultSet rs=executeQueryObserved(ps)) {
                if(!rs.next())return Optional.empty();
                if(properties==null && query.hasRightOrFullJoin() &&
                    rs.getObject(m.idColumnIndex())==null)return Optional.empty();
                return Optional.ofNullable(projection.read(rs));
            }
        }
    }
    private <T> List<T> query(Connection c,String sql,StatementBinder binder,RowMapper<T> mapper) throws SQLException {
        try(PreparedStatement ps=prepareRead(c,sql)) {
            boundRows(ps);
            Objects.requireNonNull(binder).bind(ps);
            try(ResultSet rs=executeQueryObserved(ps)) {ArrayList<T> result=new ArrayList<>();while(rs.next()){checkRows(result.size()+1);result.add(mapper.read(rs));}return result;}
        }
    }
    private <T> T one(Connection c,String sql,StatementBinder binder,RowMapper<T> mapper) throws SQLException {
        try(PreparedStatement ps=prepareRead(c,sql)) {
            ps.setMaxRows(1);
            Objects.requireNonNull(binder).bind(ps);
            try(ResultSet rs=executeQueryObserved(ps)) {return rs.next()?mapper.read(rs):null;}
        }
    }
    private int execute(Connection c,String sql,StatementBinder binder) throws SQLException {
        try(PreparedStatement ps=prepare(c,sql)) {Objects.requireNonNull(binder).bind(ps);return executeUpdateObserved(ps);}
    }
    /** Execute a DML statement that yields rows; never truncate silently. */
    private <T> List<T> executeReturning(Connection c,String sql,StatementBinder binder,
                                          RowMapper<T> mapper) throws SQLException {
        Objects.requireNonNull(sql,"sql");
        Objects.requireNonNull(mapper,"mapper");
        if(c.getAutoCommit())
            throw new IllegalStateException("RETURNING must run inside a transaction");
        try(PreparedStatement ps=prepareRead(c,sql)) {
            boundRows(ps);
            Objects.requireNonNull(binder,"binder").bind(ps);
            ArrayList<T> rows=new ArrayList<>();
            try(ResultSet rs=executeReturningObserved(ps)) {
                while(rs.next()) {
                    checkRows(rows.size()+1);
                    rows.add(mapper.read(rs));
                }
            }
            return List.copyOf(rows);
        }
    }
    private ResultSet executeReturningObserved(PreparedStatement ps) throws SQLException {
        SqlObserver observer=sqlObserver;
        if(observer==null)return ps.executeQuery();
        long start=System.nanoTime();boolean ok=false;
        try {ResultSet rs=ps.executeQuery();ok=true;return rs;}
        finally {notifyObserver(observer,SqlOperation.UPDATE,start,ok);}
    }
    private <T> void forEach(Connection c,Select<T> query,Consumer<T> consumer) throws SQLException {
        Objects.requireNonNull(consumer);
        EntityMapper<T> m=mapper(query.type());
        if (!m.children().isEmpty()) {readGraph(c,query,consumer,false);return;}
        if (!m.parents().isEmpty()) {readParents(c,query,consumer,false);return;}
        try(PreparedStatement ps=prepareRead(c,query.sql(m,type->mapper(type),dialect))) {
            query.bind(ps,dialect);
            try(ResultSet rs=executeQueryObserved(ps)){while(rs.next())consumer.accept(m.read(rs));}
        }
    }
    /**
     * The complete stream lifecycle is inside the active JDBC transaction.
     * PostgreSQL requires autoCommit=false for positive fetch-size cursor
     * retrieval. A returned Stream is never allowed to outlive this method.
     */
    private <T,R> R withStreamRows(Connection c,Select<T> query,
            Function<Stream<T>,R> work) throws SQLException {
        Objects.requireNonNull(query,"query");
        Objects.requireNonNull(work,"work");
        EntityMapper<T> mapper=mapper(query.type());
        if(!mapper.children().isEmpty() || !mapper.parents().isEmpty())
            throw new UnsupportedOperationException("Stream cursor supports flat entities only; use forEach for graphs");
        String sql=query.sql(mapper,type->mapper(type),dialect);
        Thread owner=Thread.currentThread();
        try(PreparedStatement ps=prepareRead(c,sql)) {
            query.bind(ps,dialect);
            try(ResultSet rs=executeQueryObserved(ps)) {
                Iterator<T> iterator=new Iterator<>() {
                    private boolean fetched;
                    private boolean available;
                    private boolean exhausted;
                    private void checkThread() {
                        if(Thread.currentThread()!=owner)
                            throw new IllegalStateException("JDBC Stream must be consumed on the transaction owner thread");
                    }
                    @Override public boolean hasNext() {
                        checkThread();
                        if(exhausted)return false;
                        if(!fetched) {
                            try {
                                available=false;
                                while(rs.next()) {
                                    if(query.hasRightOrFullJoin() &&
                                        rs.getObject(mapper.idColumnIndex())==null)continue;
                                    available=true;break;
                                }
                                exhausted=!available;
                                fetched=true;
                            } catch(SQLException error) {
                                throw new BjormException("Cursor iteration failed",error);
                            }
                        }
                        return available;
                    }
                    @Override public T next() {
                        if(!hasNext())throw new NoSuchElementException();
                        fetched=false;
                        try {return mapper.read(rs);}
                        catch(SQLException error){throw new BjormException("Cursor row mapping failed",error);}
                    }
                };
                try(Stream<T> rows=StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(iterator,Spliterator.ORDERED|Spliterator.NONNULL),
                    false)) {
                    return work.apply(rows);
                }
            }
        }
    }
    private <T> void scan(Connection c,String sql,StatementBinder binder,RowMapper<T> mapper,Consumer<T> consumer) throws SQLException {
        Objects.requireNonNull(consumer);
        try(PreparedStatement ps=prepareRead(c,sql)) {
            Objects.requireNonNull(binder).bind(ps);
            try(ResultSet rs=executeQueryObserved(ps)){while(rs.next())consumer.accept(mapper.read(rs));}
        }
    }
    /** JDBC batching in chunks; callers needing atomicity must use tx.batchInsert/Update. */
    private <T> void batch(Connection c,List<T> entities,boolean updating) throws SQLException {
        Objects.requireNonNull(entities);
        if(entities.isEmpty())return;
        T first=Objects.requireNonNull(entities.getFirst());
        EntityMapper<T> m=mapper(first.getClass());
        if(!updating && !m.children().isEmpty())
            throw new IllegalArgumentException("Use insert()/insertReturning() for entity graphs; batchInsert would discard children");
        if(!updating && m.generatedId())throw new IllegalArgumentException("Batch generated ids not yet supported; use insert()");
        try(PreparedStatement ps=prepare(c,updating?m.updateSql(dialect):m.insertSql(dialect))) {
            int pending=0;
            for(T entity:entities) {
                if(entity==null||entity.getClass()!=first.getClass())throw new IllegalArgumentException("Batch requires entities of one exact type");
                if(updating)m.bindUpdate(ps,m.materializeUpdate(entity),dialect);
                else { requireReturnForImmutable(entity); m.bindInsert(ps,m.materializeInsert(entity),dialect); }
                ps.addBatch();
                if(++pending==options.batchSize()){validateBatch(executeBatchObserved(ps),m,updating);pending=0;}
            }
            if(pending>0)validateBatch(executeBatchObserved(ps),m,updating);
        }
    }
    private static void validateBatch(int[] counts,EntityMapper<?> m,boolean updating) throws SQLException {
        for(int count:counts) {
            if(count==Statement.EXECUTE_FAILED)throw new SQLException("JDBC batch item failed");
            if(updating&&m.optimisticLocking()&&count!=1)throw new OptimisticLockException("Cannot confirm optimistic batch update (affected="+count+"): "+m.type().getName());
        }
    }
    /** Scope is single-use and thread-confined. Never share across threads or retain after tx returns. */
    public final class Transaction implements Operations {
        private final Connection c;
        private final Thread owner=Thread.currentThread();
        private volatile boolean active=true;
        /** Per transaction and therefore per Bjorm tenant context; never shared between requests. */
        private final Map<Class<?>,Map<Object,Object>> identityMap=new HashMap<>();
        private Transaction(Connection c) {this.c=c;}
        public SqlDialect dialect(){return Bjorm.this.dialect;}
        private <T> T use(JdbcWork<T> work) {
            if(!active||owner!=Thread.currentThread())throw new IllegalStateException("Transaction no longer active or accessed from a different thread");
            try{return work.run(c);}catch(SQLException e){throw new BjormException("Transaction JDBC operation failed",e);}
        }
        private <T> T useWrite(JdbcWork<T> work) {
            return use(c->{identityMap.clear();return work.run(c);});
        }
        public <T> void insert(T value) {requireReturnForImmutable(value);insertReturning(value);}
        public <T> T insertReturning(T value) {return useWrite(c->Bjorm.this.persistGraph(c,value,false,new IdentityHashMap<>()));}
        public <T> T upsertReturning(T value) {return useWrite(c->{
            T persisted=Bjorm.this.persistGraph(c,value,true,new IdentityHashMap<>());
            EntityMapper<T> mapper=Bjorm.this.mapper(value.getClass());
            return mapper.optimisticLocking() || mapper.upsertNeedsReload()
                ?Bjorm.this.find(c,mapper.type(),mapper.id(persisted)):persisted;
        });}
        public <T> int upsert(T value) {requireReturnForImmutable(value);return useWrite(c->{
            EntityMapper<T> mapper=Bjorm.this.mapper(value.getClass());
            if(mapper.children().isEmpty()) return Bjorm.this.upsert(c,value);
            Bjorm.this.persistGraph(c,value,true,new IdentityHashMap<>());
            return 1;
        });}
        public <T> T insertDetached(T value) {
            return insertReturning(Bjorm.this.detachedWriteCopy(value));
        }
        public <T> T upsertDetached(T value) {
            return upsertReturning(Bjorm.this.detachedWriteCopy(value));
        }
        public <T> int update(T value) {return useWrite(c->Bjorm.this.update(c,value));}
        public <T> T updateReturning(T value) {return useWrite(c->{
            Objects.requireNonNull(value);
            EntityMapper<T> mapper=Bjorm.this.mapper(value.getClass());
            if(Bjorm.this.update(c,value)==0)
                return null;
            return Bjorm.this.find(c,mapper.type(),mapper.id(value));
        });}
        public <T> int updateBy(Class<T> type,Map<String,?> changes,SqlPredicate predicate) {
            return useWrite(c->Bjorm.this.updateBy(c,type,changes,predicate));
        }
        public <T> int deleteBy(Class<T> type,SqlPredicate predicate) {
            return useWrite(c->Bjorm.this.deleteBy(c,type,predicate));
        }
        public <T> int delete(T value) {return useWrite(c->Bjorm.this.removeGraph(c,value,new HashSet<>()));}
        public <T> T find(Class<T> type,Object id) {
            Objects.requireNonNull(id,"id required for find()");
            return use(c->{
                // Limit identity reuse to this thread-confined transaction. Writes and opaque
                // native SQL invalidate the map, so no result can outlive a transaction.
                Map<Object,Object> rows=identityMap.get(type);
                if(rows!=null && rows.containsKey(id))return type.cast(rows.get(id));
                T result=Bjorm.this.find(c,type,id);
                if(result!=null)identityMap.computeIfAbsent(type,ignored->new HashMap<>()).put(id,result);
                return result;
            });
        }
        public <T> List<T> list(Class<T> type,SqlPredicate where) {return list(select(type).whereNullable(where));}
        public <T> long count(Class<T> type,SqlPredicate where) {return use(c->Bjorm.this.count(c,type,where));}
        public <T> boolean exists(Class<T> type,SqlPredicate where) {return use(c->Bjorm.this.exists(c,type,where));}
        public <T> boolean lockOne(Class<T> type,SqlPredicate where,LockMode mode) {
            return useWrite(c->Bjorm.this.lockOne(c,type,where,mode));
        }
        public <T> List<T> lockRows(Class<T> type,SqlPredicate where,LockMode mode,int limit) {
            return useWrite(c->Bjorm.this.lockRows(c,type,where,mode,limit));
        }
        public <T> List<T> list(Select<T> query) {return use(c->Bjorm.this.list(c,query));}
        public <T> Optional<T> first(Select<T> query) {query.limit(1);return use(c->{if(!mapper(query.type()).children().isEmpty() || !mapper(query.type()).parents().isEmpty()){List<T> values=Bjorm.this.list(c,query);return values.stream().findFirst();}return Bjorm.this.first(c,query,null,mapper(query.type()));});}
        public <T> List<Map<String,Object>> fieldRows(Select<T> query,String[] properties) {
            return use(c->{EntityMapper<T> m=mapper(query.type());return Bjorm.this.project(c,query,properties,rowProperties(m,properties));});
        }
        public <T> Optional<Map<String,Object>> fieldOne(Select<T> query,String[] properties) {
            query.limit(1);
            return use(c->{EntityMapper<T> m=mapper(query.type());return Bjorm.this.first(c,query,properties,rowProperties(m,properties));});
        }
        public <T,P> List<P> project(Select<T> query,String[] properties,RowMapper<P> projection) {
            return use(c->Bjorm.this.project(c,query,properties,projection));
        }
        public <T,P> Optional<P> projectOne(Select<T> query,String[] properties,RowMapper<P> projection) {
            query.limit(1);
            return use(c->Bjorm.this.first(c,query,properties,projection));
        }
        public <T,C> List<C> columnValues(Select<T> query,String property,Class<C> type) {
            return use(c->Bjorm.this.scalarList(c,query,property,type));
        }
        public <T,C> Optional<C> columnOne(Select<T> query,String property,Class<C> type) {
            query.limit(1);
            return use(c->Bjorm.this.scalarOne(c,query,property,type));
        }
        public <T> List<T> query(String sql,StatementBinder bind,RowMapper<T> mapper) {requireNativeSqlPermission();return useWrite(c->Bjorm.this.query(c,sql,bind,mapper));}
        public <T> T one(String sql,StatementBinder bind,RowMapper<T> mapper) {requireNativeSqlPermission();return useWrite(c->Bjorm.this.one(c,sql,bind,mapper));}
        public int execute(String sql,StatementBinder bind) {requireNativeSqlPermission();return useWrite(c->Bjorm.this.execute(c,sql,bind));}
        public <T> List<T> executeReturning(String sql,StatementBinder binder,RowMapper<T> mapper) {
            requireNativeSqlPermission();
            requireReturningDialect();
            return useWrite(c->Bjorm.this.executeReturning(c,sql,binder,mapper));
        }
        public <T> void forEach(Class<T> type,SqlPredicate where,Consumer<T> consumer) {forEach(select(type).whereNullable(where),consumer);}
        public <T> void forEach(Select<T> query,Consumer<T> consumer) {use(c->{Bjorm.this.forEach(c,query,consumer);return null;});}
        public <T,R> R withStream(Select<T> query,Function<Stream<T>,R> work) {
            return use(c->Bjorm.this.withStreamRows(c,query,work));
        }
        public <T> void scan(String sql,StatementBinder binder,RowMapper<T> mapper,Consumer<T> consumer) {requireNativeSqlPermission();useWrite(c->{Bjorm.this.scan(c,sql,binder,mapper,consumer);return null;});}
        public <T> void batchInsert(List<T> list) {useWrite(c->{Bjorm.this.batch(c,list,false);return null;});}
        public <T> List<T> batchInsertReturning(List<T> list) {return useWrite(c->{
            Objects.requireNonNull(list);
            if(list.isEmpty())return List.of();
            EntityMapper<T> m=mapper(Objects.requireNonNull(list.getFirst()).getClass());
            if(!m.children().isEmpty())throw new IllegalArgumentException("Use insertReturning for entity graphs");
            if(m.generatedId()) {
                List<T> saved=new ArrayList<>(list.size());
                for(T v:list)saved.add(Bjorm.this.insert(c,v));
                return saved;
            }
            List<T> saved=new ArrayList<>(list.size());
            for(T v:list){
                if(v==null||v.getClass()!=m.type())throw new IllegalArgumentException("Batch requires one entity type");
                saved.add(m.materializeInsert(v));
            }
            Bjorm.this.batch(c,saved,false);
            return saved;
        });}
        public <T> void batchUpdate(List<T> list) {useWrite(c->{Bjorm.this.batch(c,list,true);return null;});}
        public <T> List<T> batchUpdateReturning(List<T> values) {return useWrite(c->{
            Objects.requireNonNull(values);
            List<T> refreshed=new ArrayList<>(values.size());
            Class<?> expected=null;
            for(T value:values) {
                Objects.requireNonNull(value,"batchUpdate entity");
                if(expected==null)expected=value.getClass();
                else if(value.getClass()!=expected)throw new IllegalArgumentException("Batch requires one entity type");
                EntityMapper<T> mapper=Bjorm.this.mapper(value.getClass());
                if(Bjorm.this.update(c,value)==0)
                    throw new IllegalStateException("Batch update affected no row: "+mapper.type().getName());
                T current=Bjorm.this.find(c,mapper.type(),mapper.id(value));
                if(current==null)throw new IllegalStateException("Updated entity cannot be reloaded");
                refreshed.add(current);
            }
            return List.copyOf(refreshed);
        });}
        public <T> void batchDelete(List<T> values) {useWrite(c->{
            Objects.requireNonNull(values);
            Class<?> expected=null;
            for(T value:values) {
                Objects.requireNonNull(value,"batchDelete entity");
                if(expected==null)expected=value.getClass();
                else if(value.getClass()!=expected)throw new IllegalArgumentException("Batch requires one entity type");
                Bjorm.this.removeGraph(c,value,new HashSet<>());
            }
            return null;
        });}
        public <T> List<T> batchUpsertReturning(List<T> values) {return useWrite(c->{
            Objects.requireNonNull(values);
            List<T> persisted=new ArrayList<>(values.size());
            Class<?> expected=null;
            for(T value:values) {
                Objects.requireNonNull(value,"batchUpsert entity");
                if(expected==null)expected=value.getClass();
                else if(value.getClass()!=expected)throw new IllegalArgumentException("Batch requires one entity type");
                T prepared=Bjorm.this.persistGraph(c,value,true,new IdentityHashMap<>());
                EntityMapper<T> mapper=Bjorm.this.mapper(value.getClass());
                persisted.add(mapper.optimisticLocking() || mapper.upsertNeedsReload()
                    ?Bjorm.this.find(c,mapper.type(),mapper.id(prepared)):prepared);
            }
            return List.copyOf(persisted);
        });}
    }
}
