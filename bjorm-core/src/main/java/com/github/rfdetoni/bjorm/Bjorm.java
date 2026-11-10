package com.github.rfdetoni.bjorm;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Explicit, immutable instance-owned mapper registry, no globally scoped context. */
public final class Bjorm implements Operations {
    private final DataSource dataSource;
    private final BjormOptions options;
    private final SqlDialect dialect;
    private final Map<Class<?>, EntityMapper<?>> mappers;
    private final boolean tenantRequired;
    private final String tenantSchema;
    private static final java.util.regex.Pattern TENANT_IDENTIFIER =
        java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z_0-9]{0,62}");

    /** Immutable tenant-bound view. Never share an unscoped connection in tenant-required mode. */
    private Bjorm(Bjorm source, String tenantSchema) {
        this.dataSource=source.dataSource;
        this.options=source.options;
        this.dialect=source.dialect;
        this.mappers=source.mappers;
        this.tenantRequired=true;
        this.tenantSchema=tenantSchema;
    }
    private Bjorm(DataSource dataSource,BjormOptions options,SqlDialect dialect,EntityMapper<?>... registered) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.options = Objects.requireNonNull(options, "options");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        Map<Class<?>, EntityMapper<?>> byType = new HashMap<>();
        for(EntityMapper<?> m : registered) if(byType.putIfAbsent(m.type(), m)!=null)
            throw new IllegalArgumentException("Duplicate mapper: " + m.type());
        mappers = Map.copyOf(byType);
        tenantRequired=false;
        tenantSchema=null;
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
        return new Bjorm(open(source,options,dialect,registered),null);
    }
    public static Bjorm tenantRequired(DataSource source) {
        return new Bjorm(open(source),null);
    }
    /** Tenant schema must come from an authorized server-side tenant registry, not request input. */
    public Bjorm forTenant(String schema) {
        if (!tenantRequired) throw new IllegalStateException("forTenant requires a tenantRequired BJORM root");
        Objects.requireNonNull(schema,"schema");
        if (!TENANT_IDENTIFIER.matcher(schema).matches() ||
            schema.toLowerCase(Locale.ROOT).startsWith("pg_") || Set.of("public","information_schema","mysql",
                "performance_schema","sys","pg_catalog").contains(schema.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Unsafe tenant schema identifier");
        if(tenantSchema!=null)throw new IllegalStateException("Cannot rebind an already-scoped BJORM instance");
        return new Bjorm(this,schema);
    }
    public SqlDialect dialect(){return dialect;}
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
        requireTenant();
        try(Connection c=dataSource.getConnection()) {
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
        return txResult(tx->tx.insertReturning(entity));
    }
    public <T> int update(T entity) {Objects.requireNonNull(entity);return withConnection(c->update(c,entity));}
    public <T> int delete(T entity) {
        Objects.requireNonNull(entity);
        if(mapper(entity.getClass()).children().isEmpty()) return withConnection(c->delete(c,entity));
        return txResult(tx->tx.delete(entity));
    }
    public <T> int upsert(T entity) {
        requireReturnForImmutable(entity);
        if(mapper(entity.getClass()).children().isEmpty()) return withConnection(c->upsert(c,entity));
        return txResult(tx->tx.upsert(entity));
    }
    public <T> T upsertReturning(T entity) {
        Objects.requireNonNull(entity);
        EntityMapper<T> m=mapper(entity.getClass());
        if(m.children().isEmpty() && !m.optimisticLocking()) return withConnection(c->{
            T prepared=m.materializeInsert(entity);
            upsertPrepared(c,prepared);
            return prepared;
        });
        return txResult(tx->tx.upsertReturning(entity));
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
        String sql = "SELECT COUNT(*) FROM " + mapper(type).table() +
            (where == null ? "" : " WHERE " + where.sql());
        try (PreparedStatement ps = prepareRead(c,sql)) {
            bindPredicate(ps,where);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("COUNT returned no row");
                return rs.getLong(1);
            }
        }
    }
    private <T> boolean exists(Connection c, Class<T> type, SqlPredicate where) throws SQLException {
        String sql = "SELECT 1 FROM " + mapper(type).table() +
            (where == null ? "" : " WHERE " + where.sql()) + " LIMIT 1";
        try (PreparedStatement ps = prepareRead(c,sql)) {
            bindPredicate(ps,where);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }
    /** Only meaningful on a live transaction. SELECT ... LIMIT 1 FOR UPDATE/SHARE. */
    public <T> boolean lockOne(Class<T> type, SqlPredicate where, LockMode mode) {
        return withConnection(c -> lockOne(c,type,where,mode));
    }
    private <T> boolean lockOne(Connection c, Class<T> type, SqlPredicate where,
                                LockMode mode) throws SQLException {
        Objects.requireNonNull(where, "Row lock requires a predicate");
        Objects.requireNonNull(mode, "mode");
        if (c.getAutoCommit())
            throw new IllegalStateException("BJORM row locking requires an active transaction");
        EntityMapper<T> m=mapper(type);
        String sql = "SELECT " + m.columnFor(m.idProperty()) + " FROM " + m.table() +
            " WHERE " + where.sql() + " LIMIT 1 " + mode.sql();
        try (PreparedStatement ps = prepareRead(c,sql)) {
            bindPredicate(ps,where);
            try (ResultSet rs=ps.executeQuery()) { return rs.next(); }
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
        return new SqlPredicate(m.columnFor(m.idProperty())+(descending?" < ?":" > ?"),
            java.util.Collections.singletonList(id));
    }
    public <T> SqlOrder primaryKeyOrder(Class<T> type, boolean descending) {
        EntityMapper<T> m=mapper(type);
        return mappedOrder(type,m.idProperty(),descending);
    }
    /** Generated access to @Id; no per-row reflection. */
    public <T> Object primaryKeyValue(T entity) {
        Objects.requireNonNull(entity);
        return mapper(entity.getClass()).id(entity);
    }
    public <T> List<T> list(Select<T> query) {return withConnection(c->list(c,query));}
    public <T> Optional<T> first(Select<T> query) {query.limit(1);return withConnection(c->{if(!mapper(query.type()).children().isEmpty()){List<T> values=list(c,query);return values.stream().findFirst();}return first(c,query,null,mapper(query.type()));});}
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
    public <T> List<T> query(String sql,StatementBinder bind,RowMapper<T> mapper) {return withConnection(c->query(c,sql,bind,mapper));}
    public <T> T one(String sql,StatementBinder bind,RowMapper<T> mapper) {return withConnection(c->one(c,sql,bind,mapper));}
    public int execute(String sql,StatementBinder bind) {return withConnection(c->execute(c,sql,bind));}
    public <T> void forEach(Class<T> type,SqlPredicate where,Consumer<T> consumer) {
        forEach(select(type).whereNullable(where),consumer);
    }
    public <T> void forEach(Select<T> query,Consumer<T> consumer) { tx(tx->tx.forEach(query,consumer)); }
    public <T> void scan(String sql,StatementBinder binder,RowMapper<T> mapper,Consumer<T> consumer) {tx(tx->tx.scan(sql,binder,mapper,consumer));}
    public <T> void batchInsert(List<T> values) {tx(tx->tx.batchInsert(values));}
    public <T> List<T> batchInsertReturning(List<T> values) {return txResult(tx->tx.batchInsertReturning(values));}
    public <T> void batchUpdate(List<T> values) {tx(tx->tx.batchUpdate(values));}
    public <T> void batchDelete(List<T> values) {tx(tx->tx.batchDelete(values));}
    public <T> List<T> batchUpsertReturning(List<T> values) {return txResult(tx->tx.batchUpsertReturning(values));}
    public <T> T updateReturning(T entity) {return txResult(tx->tx.updateReturning(entity));}
    public <T> int updateBy(Class<T> type,Map<String,?> changes,SqlPredicate predicate) {
        return txResult(tx->tx.updateBy(type,changes,predicate));
    }
    public <T> int deleteBy(Class<T> type,SqlPredicate predicate) {
        return txResult(tx->tx.deleteBy(type,predicate));
    }

    /** Explicit transaction. Use TransactionAwareDataSourceProxy for framework-managed calls, not nested db.tx. */
    public void tx(Consumer<Transaction> work) {txResult(tx->{work.accept(tx);return null;});}
    public <R> R txResult(Function<Transaction,R> work) {
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
            if(!previous) throw new IllegalStateException("Connection already transactional; do not nest Bjorm.tx in another transaction");
            c.setAutoCommit(false);
            Transaction tx=new Transaction(c);
            Throwable failure=null;
            try {
                if(postgresTenant())applyPostgresTenant(c);
                R result=work.apply(tx);
                c.commit();
                return result;
            } catch(RuntimeException|Error e) {
                failure=e;
                try {c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}
                throw e;
            } catch(SQLException e) {
                failure=e;
                try {c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}
                throw new BjormException("Commit failed",e);
            } finally {
                tx.active=false;
                try {c.setAutoCommit(previous);} catch(SQLException restore) {
                    if(failure!=null)failure.addSuppressed(restore);
                    else throw new BjormException("Could not restore connection state",restore);
                }
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
            if(ps.executeUpdate()!=1)throw new SQLException("Insert affected unexpected number of rows");
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
        return upsertPrepared(c,m.materializeInsert(entity));
    }
    /** Accepts an already materialized entity; never invokes user preparation twice. */
    private <T> int upsertPrepared(Connection c,T entity) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        if(m.generatedId()) throw new IllegalArgumentException("Upsert requires an application-assigned primary key");
        if(m.id(entity)==null) throw new IllegalArgumentException("Upsert requires non-null ID");
        // A MySQL AUTO_INCREMENT key may silently change on UPDATE id=NULL.
        // PK-only conflict checks are consequently safe only for textual/UUID IDs.
        if("mysql".equals(dialect.name()) && !(m.id(entity) instanceof UUID || m.id(entity) instanceof String))
            throw new UnsupportedOperationException("MySQL UPSERT requires UUID/String primary key; use insert/update for numeric IDs");
        try(PreparedStatement ps=prepare(c,m.upsertSql(dialect))) {
            m.bindUpsert(ps,entity,dialect);
            int changed=ps.executeUpdate();
            if(m.optimisticLocking() && changed==0) throw new OptimisticLockException("Stale upsert: "+m.type().getName());
            return changed;
        }
    }
    private <T> T persistGraph(Connection c,T entity,boolean upserting,IdentityHashMap<Object,Boolean> visited) throws SQLException {
        if(visited.put(entity,Boolean.TRUE)!=null) throw new IllegalArgumentException("Cycle or repeated entity in persistence graph");
        if(upserting) {entity=this.<T>mapper(entity.getClass()).materializeInsert(entity);upsertPrepared(c,entity);}
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
                    ps.executeUpdate();
                }
            } else {
                // Descendants must be removed before the child: do not depend on loaded collections.
                try(PreparedStatement ps=prepareRead(c,"SELECT "+childMapper.qualifiedColumns("c")+" FROM "+childMapper.table()+" c WHERE c."+fk+" = ?")) {
                    childMapper.bindId(ps,1,id,dialect);
                    try(ResultSet rs=ps.executeQuery()) {
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
        StringJoiner assignments=new StringJoiner(", ");
        List<Object> values=new ArrayList<>(changes.size());
        for(var entry:changes.entrySet()) {
            String property=Objects.requireNonNull(entry.getKey(),"property");
            if(property.equals(m.idProperty())) throw new IllegalArgumentException("Bulk mutation cannot alter @Id");
            String column=m.columnFor(property); // generated mapping; rejects unknown input
            assignments.add(column+" = "+m.bulkParameterSql(property,dialect));
            values.add(m.bulkParameterValue(property,entry.getValue()));
        }
        String sql="UPDATE "+m.table()+" SET "+assignments+" WHERE "+predicate.sql();
        try(PreparedStatement ps=prepare(c,sql)) {
            int i=1;
            for(Object value:values)dialect.bindValue(ps,i++,value);
            for(Object value:predicate.params())dialect.bindValue(ps,i++,value);
            return ps.executeUpdate();
        }
    }
    private <T> int deleteBy(Connection c,Class<T> type,SqlPredicate predicate) throws SQLException {
        requireBulkSafe(type,predicate);
        EntityMapper<T> m=mapper(type);
        try(PreparedStatement ps=prepare(c,"DELETE FROM "+m.table()+" WHERE "+predicate.sql())) {
            bindPredicate(ps,predicate);
            return ps.executeUpdate();
        }
    }
    private <T> int update(Connection c,T entity) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        try(PreparedStatement ps=prepare(c,m.updateSql(dialect))) {
            m.bindUpdate(ps,entity,dialect);
            int count=ps.executeUpdate();
            if(m.optimisticLocking() && count==0)throw new OptimisticLockException("Stale update: " + m.type().getName());
            return count;
        }
    }
    private <T> int delete(Connection c,T entity) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        try(PreparedStatement ps=prepare(c,m.deleteSql(dialect))) {
            m.bindDelete(ps,entity,dialect);
            int count=ps.executeUpdate();
            if(m.optimisticLocking() && count==0)throw new OptimisticLockException("Stale delete: " + m.type().getName());
            return count;
        }
    }
    private <T> T find(Connection c,Class<T> type,Object id) throws SQLException {
        EntityMapper<T> m=mapper(type);
        if (!m.children().isEmpty()) {
            List<T> values = list(c,new Select<>(this,type).where(new SqlPredicate(
                m.columnFor(m.idProperty())+" = ?",Collections.singletonList(id))).limit(1));
            return values.isEmpty() ? null : values.getFirst();
        }
        try(PreparedStatement ps=prepareRead(c,m.selectSql(dialect))) {
            m.bindId(ps,1,id,dialect);
            try(ResultSet rs=ps.executeQuery()) {return rs.next()?m.read(rs):null;}
        }
    }
    private <T> List<T> list(Connection c,Select<T> query) throws SQLException {
        EntityMapper<T> m=mapper(query.type());
        if (!m.children().isEmpty()) {
            ArrayList<T> rows=new ArrayList<>();
            readGraph(c,query,rows::add,true);
            return rows;
        }
        try(PreparedStatement ps=prepareRead(c,query.sql(m,type->mapper(type),dialect))) {
            boundRows(ps);
            query.bind(ps,dialect);
            try(ResultSet rs=ps.executeQuery()) {
                ArrayList<T> result=new ArrayList<>();
                while(rs.next()){
                    if(query.hasRightOrFullJoin() && rs.getObject(m.idColumnIndex())==null)continue;
                    checkRows(result.size()+1);result.add(m.read(rs));
                }
                return result;
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
        if(query.hasJoins())rootSql="SELECT DISTINCT * FROM ("+rootSql+") bjorm_unique_roots";
        int rootColumns=root.columnCount();
        StringBuilder sql=new StringBuilder("WITH roots AS (").append(rootSql).append(") ");
        for(int branch=0;branch<paths.size();branch++) {
            if(branch>0)sql.append(" UNION ALL ");
            List<Integer> path=paths.get(branch);
            sql.append("SELECT ").append(root.qualifiedColumns("r")).append(", ").append(branch);
            for(int i=0;i<slots.size();i++) {
                GraphSlot slot=slots.get(i);
                if(path.contains(i)) sql.append(", ").append(slot.mapper.qualifiedColumns(slot.alias));
                else for(int col=0;col<slot.mapper.columnCount();col++)sql.append(", NULL");
            }
            sql.append(" FROM roots r");
            for(int i:path) {
                GraphSlot slot=slots.get(i);
                EntityMapper<Object> parent=slot.parent==0?(EntityMapper<Object>)root:slots.get(slot.parent-1).mapper;
                String parentAlias=slot.parent==0?"r":slots.get(slot.parent-1).alias;
                sql.append(' ').append(dialect.join(slot.relation.joinType())).append(' ').append(slot.mapper.table()).append(' ').append(slot.alias)
                   .append(" ON ").append(slot.alias).append('.').append(slot.mapper.columnFor(slot.relation.mappedBy()))
                   .append(" = ").append(parentAlias).append('.').append(parent.columnFor(parent.idProperty()));
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
            try(ResultSet rs=ps.executeQuery()) {
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
                        current=new GraphNode((EntityMapper<Object>)root,root.read(rs));
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
                            child=new GraphNode(slot.mapper,slot.mapper.readAt(rs,slot.offset));
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
            slots.add(new GraphSlot(child,relation,parentSlot,offset,"c"+index));
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
            try(ResultSet rs=ps.executeQuery()){
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
            try(ResultSet rs=ps.executeQuery()) {
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
            try(ResultSet rs=ps.executeQuery()) {ArrayList<T> result=new ArrayList<>();while(rs.next()){checkRows(result.size()+1);result.add(mapper.read(rs));}return result;}
        }
    }
    private <T> T one(Connection c,String sql,StatementBinder binder,RowMapper<T> mapper) throws SQLException {
        try(PreparedStatement ps=prepareRead(c,sql)) {
            ps.setMaxRows(1);
            Objects.requireNonNull(binder).bind(ps);
            try(ResultSet rs=ps.executeQuery()) {return rs.next()?mapper.read(rs):null;}
        }
    }
    private int execute(Connection c,String sql,StatementBinder binder) throws SQLException {
        try(PreparedStatement ps=prepare(c,sql)) {Objects.requireNonNull(binder).bind(ps);return ps.executeUpdate();}
    }
    private <T> void forEach(Connection c,Select<T> query,Consumer<T> consumer) throws SQLException {
        Objects.requireNonNull(consumer);
        EntityMapper<T> m=mapper(query.type());
        if (!m.children().isEmpty()) {readGraph(c,query,consumer,false);return;}
        try(PreparedStatement ps=prepareRead(c,query.sql(m,type->mapper(type),dialect))) {
            query.bind(ps,dialect);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())consumer.accept(m.read(rs));}
        }
    }
    private <T> void scan(Connection c,String sql,StatementBinder binder,RowMapper<T> mapper,Consumer<T> consumer) throws SQLException {
        Objects.requireNonNull(consumer);
        try(PreparedStatement ps=prepareRead(c,sql)) {
            Objects.requireNonNull(binder).bind(ps);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())consumer.accept(mapper.read(rs));}
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
                if(updating)m.bindUpdate(ps,entity,dialect);
                else { requireReturnForImmutable(entity); m.bindInsert(ps,m.materializeInsert(entity),dialect); }
                ps.addBatch();
                if(++pending==options.batchSize()){validateBatch(ps.executeBatch(),m,updating);pending=0;}
            }
            if(pending>0)validateBatch(ps.executeBatch(),m,updating);
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
        private Transaction(Connection c) {this.c=c;}
        public SqlDialect dialect(){return Bjorm.this.dialect;}
        private <T> T use(JdbcWork<T> work) {
            if(!active||owner!=Thread.currentThread())throw new IllegalStateException("Transaction no longer active or accessed from a different thread");
            try{return work.run(c);}catch(SQLException e){throw new BjormException("Transaction JDBC operation failed",e);}
        }
        public <T> void insert(T value) {requireReturnForImmutable(value);insertReturning(value);}
        public <T> T insertReturning(T value) {return use(c->Bjorm.this.persistGraph(c,value,false,new IdentityHashMap<>()));}
        public <T> T upsertReturning(T value) {return use(c->{
            T persisted=Bjorm.this.persistGraph(c,value,true,new IdentityHashMap<>());
            EntityMapper<T> mapper=Bjorm.this.mapper(value.getClass());
            return mapper.optimisticLocking()
                ?Bjorm.this.find(c,mapper.type(),mapper.id(persisted)):persisted;
        });}
        public <T> int upsert(T value) {requireReturnForImmutable(value);return use(c->{
            EntityMapper<T> mapper=Bjorm.this.mapper(value.getClass());
            if(mapper.children().isEmpty()) return Bjorm.this.upsert(c,value);
            Bjorm.this.persistGraph(c,value,true,new IdentityHashMap<>());
            return 1;
        });}
        public <T> int update(T value) {return use(c->Bjorm.this.update(c,value));}
        public <T> T updateReturning(T value) {return use(c->{
            Objects.requireNonNull(value);
            EntityMapper<T> mapper=Bjorm.this.mapper(value.getClass());
            if(Bjorm.this.update(c,value)==0)
                return null;
            return Bjorm.this.find(c,mapper.type(),mapper.id(value));
        });}
        public <T> int updateBy(Class<T> type,Map<String,?> changes,SqlPredicate predicate) {
            return use(c->Bjorm.this.updateBy(c,type,changes,predicate));
        }
        public <T> int deleteBy(Class<T> type,SqlPredicate predicate) {
            return use(c->Bjorm.this.deleteBy(c,type,predicate));
        }
        public <T> int delete(T value) {return use(c->Bjorm.this.removeGraph(c,value,new HashSet<>()));}
        public <T> T find(Class<T> type,Object id) {
            Objects.requireNonNull(id,"id required for find()");
            return use(c->Bjorm.this.find(c,type,id));
        }
        public <T> List<T> list(Class<T> type,SqlPredicate where) {return list(select(type).whereNullable(where));}
        public <T> long count(Class<T> type,SqlPredicate where) {return use(c->Bjorm.this.count(c,type,where));}
        public <T> boolean exists(Class<T> type,SqlPredicate where) {return use(c->Bjorm.this.exists(c,type,where));}
        public <T> boolean lockOne(Class<T> type,SqlPredicate where,LockMode mode) {
            return use(c->Bjorm.this.lockOne(c,type,where,mode));
        }
        public <T> List<T> list(Select<T> query) {return use(c->Bjorm.this.list(c,query));}
        public <T> Optional<T> first(Select<T> query) {query.limit(1);return use(c->{if(!mapper(query.type()).children().isEmpty()){List<T> values=Bjorm.this.list(c,query);return values.stream().findFirst();}return Bjorm.this.first(c,query,null,mapper(query.type()));});}
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
        public <T> List<T> query(String sql,StatementBinder bind,RowMapper<T> mapper) {return use(c->Bjorm.this.query(c,sql,bind,mapper));}
        public <T> T one(String sql,StatementBinder bind,RowMapper<T> mapper) {return use(c->Bjorm.this.one(c,sql,bind,mapper));}
        public int execute(String sql,StatementBinder bind) {return use(c->Bjorm.this.execute(c,sql,bind));}
        public <T> void forEach(Class<T> type,SqlPredicate where,Consumer<T> consumer) {forEach(select(type).whereNullable(where),consumer);}
        public <T> void forEach(Select<T> query,Consumer<T> consumer) {use(c->{Bjorm.this.forEach(c,query,consumer);return null;});}
        public <T> void scan(String sql,StatementBinder binder,RowMapper<T> mapper,Consumer<T> consumer) {use(c->{Bjorm.this.scan(c,sql,binder,mapper,consumer);return null;});}
        public <T> void batchInsert(List<T> list) {use(c->{Bjorm.this.batch(c,list,false);return null;});}
        public <T> List<T> batchInsertReturning(List<T> list) {return use(c->{
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
        public <T> void batchUpdate(List<T> list) {use(c->{Bjorm.this.batch(c,list,true);return null;});}
        public <T> void batchDelete(List<T> values) {use(c->{
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
        public <T> List<T> batchUpsertReturning(List<T> values) {return use(c->{
            Objects.requireNonNull(values);
            List<T> persisted=new ArrayList<>(values.size());
            Class<?> expected=null;
            for(T value:values) {
                Objects.requireNonNull(value,"batchUpsert entity");
                if(expected==null)expected=value.getClass();
                else if(value.getClass()!=expected)throw new IllegalArgumentException("Batch requires one entity type");
                T prepared=Bjorm.this.persistGraph(c,value,true,new IdentityHashMap<>());
                EntityMapper<T> mapper=Bjorm.this.mapper(value.getClass());
                persisted.add(mapper.optimisticLocking()
                    ?Bjorm.this.find(c,mapper.type(),mapper.id(prepared)):prepared);
            }
            return List.copyOf(persisted);
        });}
    }
}
