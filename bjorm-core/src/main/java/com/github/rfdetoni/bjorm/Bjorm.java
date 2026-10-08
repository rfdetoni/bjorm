package com.github.rfdetoni.bjorm;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Explicit, immutable instance-owned mapper registry, no globally scoped context. */
public final class Bjorm implements Operations {
    private final DataSource dataSource;
    private final Map<Class<?>, EntityMapper<?>> mappers;
    private Bjorm(DataSource dataSource, EntityMapper<?>... registered) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        Map<Class<?>, EntityMapper<?>> byType = new HashMap<>();
        for(EntityMapper<?> m : registered) if(byType.putIfAbsent(m.type(), m)!=null)
            throw new IllegalArgumentException("Duplicate mapper: " + m.type());
        mappers = Map.copyOf(byType);
    }
    /** Discover compile-time generated mappers once at startup via Java ServiceLoader. */
    public static Bjorm open(DataSource dataSource) {
        ArrayList<EntityMapper<?>> discovered=new ArrayList<>();
        for(EntityMapper<?> mapper:ServiceLoader.load(EntityMapper.class))discovered.add(mapper);
        return new Bjorm(dataSource,discovered.toArray(EntityMapper<?>[]::new));
    }
    /** Explicit registration avoids discovery and works with custom mapper implementations. */
    public static Bjorm open(DataSource dataSource, EntityMapper<?>... mappers) {return new Bjorm(dataSource,mappers);}
    @SuppressWarnings("unchecked")
    private <T> EntityMapper<T> mapper(Class<?> type) {
        EntityMapper<?> mapper = mappers.get(type);
        if(mapper==null) throw new IllegalArgumentException("No generated mapper registered for " + type.getName());
        return (EntityMapper<T>)mapper;
    }
    @FunctionalInterface private interface JdbcWork<R> {R run(Connection c) throws SQLException;}
    private <R> R withConnection(JdbcWork<R> work) {
        try(Connection c=dataSource.getConnection()) {return work.run(c);}
        catch(SQLException e) {throw new BjormException("JDBC operation failed",e);}
    }
    public <T> void insert(T entity) {
        Objects.requireNonNull(entity);
        if(mapper(entity.getClass()).children().isEmpty()) withConnection(c->{insert(c,entity);return null;});
        else tx(tx->tx.insert(entity));
    }
    public <T> int update(T entity) {Objects.requireNonNull(entity);return withConnection(c->update(c,entity));}
    public <T> int delete(T entity) {
        Objects.requireNonNull(entity);
        if(mapper(entity.getClass()).children().isEmpty()) return withConnection(c->delete(c,entity));
        return txResult(tx->tx.delete(entity));
    }
    public <T> int upsert(T entity) {
        Objects.requireNonNull(entity);
        if(mapper(entity.getClass()).children().isEmpty()) return withConnection(c->upsert(c,entity));
        return txResult(tx->tx.upsert(entity));
    }
    public <T> T find(Class<T> type,Object id) {return withConnection(c->find(c,type,id));}
    public <T> List<T> list(Class<T> type,SqlPredicate where) {return select(type).whereNullable(where).fetch();}
    /** Count mapped rows matching a typed predicate without materializing entities. */
    public <T> long count(Class<T> type, SqlPredicate where) {
        return withConnection(c -> {
            EntityMapper<T> m = mapper(type);
            String sql = "SELECT COUNT(*) FROM " + m.table() + (where == null ? "" : " WHERE " + where.sql());
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (where != null) {
                    int i = 1;
                    for (Object value : where.params()) ps.setObject(i++, value);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("COUNT returned no row");
                    return rs.getLong(1);
                }
            }
        });
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
    public <T> Optional<T> first(Select<T> query) {query.limit(1);return withConnection(c->first(c,query,null,mapper(query.type())));}
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
    public <T> void batchUpdate(List<T> values) {tx(tx->tx.batchUpdate(values));}

    /** Explicit transaction. Use TransactionAwareDataSourceProxy for framework-managed calls, not nested db.tx. */
    public void tx(Consumer<Transaction> work) {txResult(tx->{work.accept(tx);return null;});}
    public <R> R txResult(Function<Transaction,R> work) {
        Objects.requireNonNull(work);
        try(Connection c=dataSource.getConnection()) {
            boolean previous=c.getAutoCommit();
            if(!previous) throw new IllegalStateException("Connection already transactional; do not nest Bjorm.tx in another transaction");
            c.setAutoCommit(false);
            Transaction tx=new Transaction(c);
            Throwable failure=null;
            try {
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
        } catch(SQLException e) {throw new BjormException("Transaction failed",e);}
    }
    private <T> void insert(Connection c,T entity) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        m.prepareInsert(entity);
        try(PreparedStatement ps=m.generatedId()?c.prepareStatement(m.insertSql(),Statement.RETURN_GENERATED_KEYS):c.prepareStatement(m.insertSql())) {
            m.bindInsert(ps,entity);
            if(ps.executeUpdate()!=1)throw new SQLException("Insert affected unexpected number of rows");
            if(m.generatedId())try(ResultSet keys=ps.getGeneratedKeys()) {
                if(!keys.next())throw new SQLException("No generated key returned for "+m.type().getName());
                m.acceptGeneratedId(keys,entity);
            }
        }
    }
    private <T> int upsert(Connection c,T entity) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        if(m.generatedId()) throw new IllegalArgumentException("Upsert requires an application-assigned primary key");
        m.prepareInsert(entity);
        if(m.id(entity)==null) throw new IllegalArgumentException("Upsert requires non-null ID");
        try(PreparedStatement ps=c.prepareStatement(m.upsertSql())) {
            m.bindUpsert(ps,entity);
            int changed=ps.executeUpdate();
            if(m.optimisticLocking() && changed==0) throw new OptimisticLockException("Stale upsert: "+m.type().getName());
            return changed;
        }
    }
    private <T> void persistGraph(Connection c,T entity,boolean upserting,IdentityHashMap<Object,Boolean> visited) throws SQLException {
        if(visited.put(entity,Boolean.TRUE)!=null) throw new IllegalArgumentException("Cycle or repeated entity in persistence graph");
        if(upserting) upsert(c,entity); else insert(c,entity);
        EntityMapper<T> m=mapper(entity.getClass());
        Object id=m.id(entity);
        if(id==null && !m.children().isEmpty()) throw new IllegalArgumentException("Parent ID is null: "+m.type());
        for(ChildRelation<T> relation:m.children()) {
            Iterable<?> children=relation.children(entity);
            if(children==null)continue;
            for(Object child:children) {
                Objects.requireNonNull(child,"Child cannot be null");
                if(!relation.childType().isInstance(child)) throw new IllegalArgumentException("Unexpected child type: "+child.getClass());
                relation.attach(id,child);
                persistGraph(c,child,upserting,visited);
            }
        }
    }
    private record DeleteKey(Class<?> type,Object id) {}
    private <T> int removeGraph(Connection c,T entity,Set<DeleteKey> visited) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        Object id=Objects.requireNonNull(m.id(entity),"Cannot delete entity with null ID");
        if(!visited.add(new DeleteKey(m.type(),id)))throw new IllegalArgumentException("Cycle in persisted entity graph");
        for(ChildRelation<T> relation:m.children()) {
            EntityMapper<Object> childMapper=mapper(relation.childType());
            String fk=childMapper.columnFor(relation.mappedBy());
            if(childMapper.children().isEmpty()) {
                try(PreparedStatement ps=c.prepareStatement("DELETE FROM "+childMapper.table()+" WHERE "+fk+" = ?")) {
                    childMapper.bindId(ps,1,id);
                    ps.executeUpdate();
                }
            } else {
                // Descendants must be removed before the child: do not depend on loaded collections.
                try(PreparedStatement ps=c.prepareStatement("SELECT "+childMapper.qualifiedColumns("c")+" FROM "+childMapper.table()+" c WHERE c."+fk+" = ?")) {
                    childMapper.bindId(ps,1,id);
                    try(ResultSet rs=ps.executeQuery()) {
                        while(rs.next())removeGraph(c,childMapper.read(rs),visited);
                    }
                }
            }
        }
        return delete(c,entity);
    }
    private <T> int update(Connection c,T entity) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        try(PreparedStatement ps=c.prepareStatement(m.updateSql())) {
            m.bindUpdate(ps,entity);
            int count=ps.executeUpdate();
            if(m.optimisticLocking() && count==0)throw new OptimisticLockException("Stale update: " + m.type().getName());
            return count;
        }
    }
    private <T> int delete(Connection c,T entity) throws SQLException {
        EntityMapper<T> m=mapper(entity.getClass());
        try(PreparedStatement ps=c.prepareStatement(m.deleteSql())) {
            m.bindDelete(ps,entity);
            int count=ps.executeUpdate();
            if(m.optimisticLocking() && count==0)throw new OptimisticLockException("Stale delete: " + m.type().getName());
            return count;
        }
    }
    private <T> T find(Connection c,Class<T> type,Object id) throws SQLException {
        EntityMapper<T> m=mapper(type);
        try(PreparedStatement ps=c.prepareStatement(m.selectSql())) {
            m.bindId(ps,1,id);
            try(ResultSet rs=ps.executeQuery()) {return rs.next()?m.read(rs):null;}
        }
    }
    private <T> List<T> list(Connection c,Select<T> query) throws SQLException {
        EntityMapper<T> m=mapper(query.type());
        try(PreparedStatement ps=c.prepareStatement(query.sql(m,type->mapper(type)))) {
            query.bind(ps);
            try(ResultSet rs=ps.executeQuery()) {
                ArrayList<T> result=new ArrayList<>();
                while(rs.next())result.add(m.read(rs));
                return result;
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
    private <T,C> List<C> scalarList(Connection c,Select<T> query,String property,Class<C> type) throws SQLException {
        EntityMapper<T> m=mapper(query.type());
        return project(c,query,new String[]{property},rs->type.cast(m.readProperty(rs,1,property)));
    }
    private <T,C> Optional<C> scalarOne(Connection c,Select<T> query,String property,Class<C> type) throws SQLException {
        EntityMapper<T> m=mapper(query.type());
        return first(c,query,new String[]{property},rs->type.cast(m.readProperty(rs,1,property)));
    }
    private <T,P> List<P> project(Connection c,Select<T> query,String[] properties,RowMapper<P> projection) throws SQLException {
        Objects.requireNonNull(projection);
        EntityMapper<T> m=mapper(query.type());
        String sql=query.sql(m,type->mapper(type),properties);
        try(PreparedStatement ps=c.prepareStatement(sql)) {
            query.bind(ps);
            try(ResultSet rs=ps.executeQuery()){
                ArrayList<P> rows=new ArrayList<>();
                while(rs.next())rows.add(projection.read(rs));
                return rows;
            }
        }
    }
    private <T,P> Optional<P> first(Connection c,Select<T> query,String[] properties,RowMapper<P> projection) throws SQLException {
        Objects.requireNonNull(projection);
        EntityMapper<T> m=mapper(query.type());
        String sql=query.sql(m,type->mapper(type),properties);
        try(PreparedStatement ps=c.prepareStatement(sql)) {
            query.bind(ps);
            try(ResultSet rs=ps.executeQuery()) {
                return rs.next()?Optional.ofNullable(projection.read(rs)):Optional.empty();
            }
        }
    }
    private <T> List<T> query(Connection c,String sql,StatementBinder binder,RowMapper<T> mapper) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement(sql)) {
            Objects.requireNonNull(binder).bind(ps);
            try(ResultSet rs=ps.executeQuery()) {ArrayList<T> result=new ArrayList<>();while(rs.next())result.add(mapper.read(rs));return result;}
        }
    }
    private <T> T one(Connection c,String sql,StatementBinder binder,RowMapper<T> mapper) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement(sql)) {
            Objects.requireNonNull(binder).bind(ps);
            try(ResultSet rs=ps.executeQuery()) {return rs.next()?mapper.read(rs):null;}
        }
    }
    private int execute(Connection c,String sql,StatementBinder binder) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement(sql)) {Objects.requireNonNull(binder).bind(ps);return ps.executeUpdate();}
    }
    private <T> void forEach(Connection c,Select<T> query,Consumer<T> consumer) throws SQLException {
        Objects.requireNonNull(consumer);
        EntityMapper<T> m=mapper(query.type());
        try(PreparedStatement ps=c.prepareStatement(query.sql(m,type->mapper(type)))) {
            ps.setFetchSize(128);
            query.bind(ps);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())consumer.accept(m.read(rs));}
        }
    }
    private <T> void scan(Connection c,String sql,StatementBinder binder,RowMapper<T> mapper,Consumer<T> consumer) throws SQLException {
        Objects.requireNonNull(consumer);
        try(PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setFetchSize(128);
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
        if(!updating && m.generatedId())throw new IllegalArgumentException("Batch generated ids not yet supported; use insert()");
        try(PreparedStatement ps=c.prepareStatement(updating?m.updateSql():m.insertSql())) {
            int pending=0;
            for(T entity:entities) {
                if(entity==null||entity.getClass()!=first.getClass())throw new IllegalArgumentException("Batch requires entities of one exact type");
                if(updating)m.bindUpdate(ps,entity);else m.bindInsert(ps,entity);
                ps.addBatch();
                if(++pending==256){validateBatch(ps.executeBatch(),m,updating);pending=0;}
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
        private <T> T use(JdbcWork<T> work) {
            if(!active||owner!=Thread.currentThread())throw new IllegalStateException("Transaction no longer active or accessed from a different thread");
            try{return work.run(c);}catch(SQLException e){throw new BjormException("Transaction JDBC operation failed",e);}
        }
        public <T> void insert(T value) {use(c->{Bjorm.this.persistGraph(c,value,false,new IdentityHashMap<>());return null;});}
        public <T> int upsert(T value) {return use(c->{
            EntityMapper<T> mapper=Bjorm.this.mapper(value.getClass());
            if(mapper.children().isEmpty()) return Bjorm.this.upsert(c,value);
            Bjorm.this.persistGraph(c,value,true,new IdentityHashMap<>());
            return 1;
        });}
        public <T> int update(T value) {return use(c->Bjorm.this.update(c,value));}
        public <T> int delete(T value) {return use(c->Bjorm.this.removeGraph(c,value,new HashSet<>()));}
        public <T> T find(Class<T> type,Object id) {return use(c->Bjorm.this.find(c,type,id));}
        public <T> List<T> list(Class<T> type,SqlPredicate where) {return list(select(type).whereNullable(where));}
        public <T> List<T> list(Select<T> query) {return use(c->Bjorm.this.list(c,query));}
        public <T> Optional<T> first(Select<T> query) {query.limit(1);return use(c->Bjorm.this.first(c,query,null,mapper(query.type())));}
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
        public <T> void batchUpdate(List<T> list) {use(c->{Bjorm.this.batch(c,list,true);return null;});}
    }
}
