package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.*;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Exercises generated repositories, POJO, projections, optimistic locking, batch and keys without external libraries. */
public final class AdvancedSmokeTest {
    private record Call(int connection,String sql,Map<Integer,Object> binds) {}
    private record PreparedOnce(UUID id,String label) {}
    /** A non-idempotent mapper proves each UPSERT materializes the entity only once. */
    private static final class CountingMapper implements EntityMapper<PreparedOnce> {
        final AtomicInteger preparations=new AtomicInteger();
        public Class<PreparedOnce> type(){return PreparedOnce.class;}
        public String insertSql(){return "INSERT INTO prepared_once (id, label) VALUES (?, ?)";}
        public String updateSql(){return "UPDATE prepared_once SET label=? WHERE id=?";}
        public String deleteSql(){return "DELETE FROM prepared_once WHERE id=?";}
        public String selectSql(){return "SELECT id, label FROM prepared_once WHERE id=?";}
        public String selectAllSql(){return "SELECT id, label FROM prepared_once";}
        public String upsertSql(){return "INSERT INTO prepared_once (id, label) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET label=EXCLUDED.label";}
        public void bindInsert(PreparedStatement ps,PreparedOnce value)throws SQLException{
            ps.setObject(1,value.id());ps.setString(2,value.label());
        }
        public void bindUpdate(PreparedStatement ps,PreparedOnce value)throws SQLException{
            ps.setString(1,value.label());ps.setObject(2,value.id());
        }
        public void bindId(PreparedStatement ps,int index,Object id)throws SQLException{ps.setObject(index,id);}
        public Object id(PreparedOnce value){return value.id();}
        public PreparedOnce read(ResultSet rs)throws SQLException{return new PreparedOnce((UUID)rs.getObject(1),rs.getString(2));}
        public PreparedOnce materializeInsert(PreparedOnce value) {
            preparations.incrementAndGet();
            return value.id()==null?new PreparedOnce(UuidV7.next(),value.label()):value;
        }
    }
    private static void check(boolean condition,String description){if(!condition)throw new AssertionError(description);}
    private static Object proxy(Class<?> type,InvocationHandler handler){return Proxy.newProxyInstance(AdvancedSmokeTest.class.getClassLoader(),new Class<?>[]{type},handler);}
    private static final class JdbcSpy {
        final List<Call> calls=new ArrayList<>();
        final List<Integer> fetchSizes=new ArrayList<>();
        final AtomicInteger connections=new AtomicInteger(),commits=new AtomicInteger(),rollbacks=new AtomicInteger();
        final AtomicInteger closedResultSets=new AtomicInteger(),closedStatements=new AtomicInteger();
        int updateResult=1;
        boolean noRows=false;
        final UUID id=UUID.randomUUID();
        DataSource dataSource(){return (DataSource)proxy(DataSource.class,(p,m,a)->switch(m.getName()){
            case "getConnection"->connection(connections.incrementAndGet());
            default->throw new UnsupportedOperationException(m.getName());
        });}
        Connection connection(int connection){
            final boolean[] auto={true};
            return (Connection)proxy(Connection.class,(p,m,a)->switch(m.getName()){
                case "prepareStatement"->prepared(connection,(String)a[0]);
                case "getAutoCommit"->auto[0];
                case "setAutoCommit"->{auto[0]=(boolean)a[0];yield null;}
                case "commit"->{commits.incrementAndGet();yield null;}
                case "rollback"->{rollbacks.incrementAndGet();yield null;}
                case "close"->null;
                default->throw new UnsupportedOperationException("Connection."+m.getName());
            });
        }
        PreparedStatement prepared(int connection,String sql){
            Map<Integer,Object> binds=new TreeMap<>();List<Map<Integer,Object>> batch=new ArrayList<>();
            return (PreparedStatement)proxy(PreparedStatement.class,(p,m,a)->switch(m.getName()){
                case "setInt","setLong","setObject","setString","setBoolean","setDouble","setFloat","setShort","setBigDecimal","setTimestamp"->{binds.put((Integer)a[0],a[1]);yield null;}
                case "addBatch"->{batch.add(new HashMap<>(binds));yield null;}
                case "executeBatch"->{int[] counts=new int[batch.size()];Arrays.fill(counts,updateResult);for(Map<Integer,Object> b:batch)calls.add(new Call(connection,sql,Map.copyOf(b)));batch.clear();yield counts;}
                case "executeUpdate"->{calls.add(new Call(connection,sql,Map.copyOf(binds)));yield updateResult;}
                case "executeQuery"->{calls.add(new Call(connection,sql,Map.copyOf(binds)));
                    if(noRows)yield result(null);
                    if(sql.startsWith("SELECT COUNT(*) FROM ")) yield result(new Object[]{3L});
                    if(sql.startsWith("SELECT 1 FROM ")) yield result(new Object[]{1});
                    if(sql.startsWith("SELECT ") && sql.contains(" FROM products")) {
                        String[] columns=sql.substring(7,sql.indexOf(" FROM products")).split(",");
                        Object[] values=new Object[columns.length];
                        for(int i=0;i<columns.length;i++){
                            String col=columns[i].trim().replaceFirst("^[a-zA-Z_][a-zA-Z_0-9]*\\.","");
                            values[i]=switch(col){
                                case "id"->id;
                                case "name"->"Pencil";
                                case "unit_price"->new BigDecimal("12.50");
                                case "status"->columns.length==1?null:"ACTIVE";
                                case "version"->0;
                                default ->null;
                            };
                        }
                        yield result(values);
                    }
                    yield result(new Object[]{id,"Pencil"});
                }
                case "getGeneratedKeys"->result(new Object[]{42L});
                case "setFetchSize"->{fetchSizes.add((Integer)a[0]);yield null;}
                case "close"->{closedStatements.incrementAndGet();yield null;}
                case "clearParameters","setQueryTimeout","setMaxRows"->null;
                default->throw new UnsupportedOperationException("PreparedStatement."+m.getName());
            });
        }
        ResultSet result(Object[] row){
            final int[] cursor={-1};final boolean[] wasNull={false};
            return (ResultSet)proxy(ResultSet.class,(p,m,a)->switch(m.getName()){
                case "next"->row!=null && ++cursor[0]==0;
                case "close"->{closedResultSets.incrementAndGet();yield null;}
                case "wasNull"->wasNull[0];
                case "getObject","getString","getBigDecimal","getInt","getLong","getBoolean","getShort","getDouble","getFloat"->{
                    Object value=row[(Integer)a[0]-1];wasNull[0]=value==null;
                    yield switch(m.getName()){
                        case "getInt"->value==null?0:((Number)value).intValue();
                        case "getLong"->value==null?0L:((Number)value).longValue();
                        case "getShort"->value==null?(short)0:((Number)value).shortValue();
                        case "getFloat"->value==null?0f:((Number)value).floatValue();
                        case "getDouble"->value==null?0d:((Number)value).doubleValue();
                        case "getString"->value==null?null:value.toString();
                        case "getBoolean"->Boolean.TRUE.equals(value);
                        default->value;
                    };
                }
                default->throw new UnsupportedOperationException("ResultSet."+m.getName());
            });
        }
        Call last(){return calls.getLast();}
    }
    public static void main(String[] args){
        JdbcSpy spy=new JdbcSpy();
        CountingMapper counting=new CountingMapper();
        Bjorm db=Bjorm.open(spy.dataSource(),counting,Product_BjormMapper.INSTANCE,Identity_BjormMapper.INSTANCE,User_BjormMapper.INSTANCE,JsonDocument_BjormMapper.INSTANCE,AutoUuidRecord_BjormMapper.INSTANCE,AutoStringRecord_BjormMapper.INSTANCE,AutoLongRecord_BjormMapper.INSTANCE,AutoIntRecord_BjormMapper.INSTANCE,AutoUuidPojo_BjormMapper.INSTANCE,AutoParentRecord_BjormMapper.INSTANCE,AutoChildRecord_BjormMapper.INSTANCE);
        Bjorm discovered=Bjorm.open(spy.dataSource());
        check(discovered.find(Product.class,spy.id)!=null,"generated mapper service discovery");
        Product p=new Product();p.setId(spy.id);p.setName("Pencil");p.setPrice(new BigDecimal("12.50"));p.setStatus(Status.ACTIVE);
        p.insert(db);
        check(spy.last().sql().startsWith("INSERT INTO products"),"POJO mapper INSERT");
        check(spy.last().binds().get(4).equals("ACTIVE"),"enum binds as name");
        int beforeInvalidFind=spy.connections.get();
        try {db.find(Product.class,null);throw new AssertionError("find(null) must be rejected");}
        catch(NullPointerException expected) {
            check(expected.getMessage().contains("id required"),"find rejects null ID explicitly");
        }
        check(spy.connections.get()==beforeInvalidFind,"find(null) does not borrow a connection");
        Product loaded=db.find(Product.class,spy.id);
        check(spy.fetchSizes.contains(db.options().fetchSize()),"ordinary find applies fetch-size hint");
        check(loaded.getStatus()==Status.ACTIVE&&loaded.getPrice().compareTo(p.getPrice())==0,"POJO generated row mapper");
        db.update(p);
        check(spy.last().sql().equals("UPDATE products SET name = ?, unit_price = ?, status = ?, version = version + 1 WHERE id = ? AND version = ?"),"optimistic UPDATE shape");
        check(spy.last().binds().get(5).equals(0),"optimistic version binder");
        spy.updateResult=0;
        try{db.tx(tx->tx.update(p));throw new AssertionError("must detect stale version");}
        catch(OptimisticLockException expected){};
        check(spy.rollbacks.get()==1,"optimistic locking rolls transaction back");
        spy.updateResult=1;
        Object[] mutableArgs={"Pencil",spy.id};
        StatementBinder captured=db.params(mutableArgs);
        mutableArgs[0]="tampered";
        check(db.execute("UPDATE products SET name=? WHERE id=?",captured)==1,
            "native positional binder executes SQL");
        check(spy.last().binds().get(1).equals("Pencil") && spy.last().binds().get(2).equals(spy.id),
            "native positional binder snapshots values and preserves UUID");
        java.time.Instant instant=java.time.Instant.parse("2026-11-10T12:00:00Z");
        db.execute("UPDATE products SET updated_at=? WHERE id=?",db.params(instant,spy.id));
        check(spy.last().binds().get(1) instanceof java.sql.Timestamp timestamp &&
              timestamp.toInstant().equals(instant),
            "native binder converts Instant through SQL dialect");
        db.tx(tx -> {
            tx.execute("UPDATE products SET name=? WHERE id=?",tx.params("in-transaction",spy.id));
            check(spy.last().binds().get(1).equals("in-transaction"),
                "native binder available through transaction Operations");
        });
        check(JsonDocument_BjormMapper.INSTANCE.insertSql().contains("CAST(? AS jsonb)"),"PostgreSQL JSON binding precompiled");
        ProductQueries repo=new ProductQueries_Bjorm(db);
        var summaries=repo.summaries("Pencil",spy.id);
        check(summaries.size()==1&&summaries.getFirst().name().equals("Pencil"),"compiled projection query");
        check(spy.last().sql().equals("SELECT id, name FROM products WHERE name = ? AND id <> ?"),"compiled named SQL");
        check(spy.last().binds().equals(Map.of(1,"Pencil",2,spy.id)),"@Query named parameter order");
        check(repo.byId(spy.id).orElseThrow().getStatus()==Status.ACTIVE,"Optional entity mapping");
        check(repo.rename("Pen",spy.id)==1,"@Query update");
        repo.literalSafety(spy.id);
        check(spy.last().sql().contains("':not_a_parameter'")&&spy.last().sql().contains("?::uuid -- :ignored"),"safe scanner preserves casts/strings/comments");
        var selected=db.select(Product.class).where(Product_.name.in(List.of("Pencil","Pen"))
                .and(Product_.version.between(0,2)).not()).orderBy(Product_.name.asc()).limit(10).offset(5).fetch();
        check(selected.size()==1,"typed selection returns rows");
        check(spy.last().sql().contains("IN (?, ?)")&&spy.last().sql().endsWith(" ORDER BY name ASC LIMIT ? OFFSET ?"),"typed query SQL shape");
        check(spy.last().binds().get(5).equals(10)&&spy.last().binds().get(6).equals(5L),"pagination values parameterized");
        check(Product_.name.in(List.of()).sql().equals("1 = 0"),"empty IN is false");
        db.select(Product.class).as("p")
            .join(User.class,"u", Product_.id.as("p").sameAs(User_.id.as("u")))
            .where(Product_.name.as("p").eq("Pencil"))
            .orderBy(Product_.name.as("p").asc()).fetch();
        check(spy.last().sql().equals("SELECT p.id, p.name, p.unit_price, p.status, p.version FROM products p INNER JOIN users u ON p.id = u.id WHERE p.name = ? ORDER BY p.name ASC"),"typed join compilation");
        check(spy.last().binds().equals(Map.of(1,"Pencil")),"typed join bind ordering");
        db.select(Product.class).as("p")
            .join(User.class,"u",Product_.id.as("p").sameAs(User_.id.as("u"))
                .and(Product_.name.as("p").eq("ON_VALUE")))
            .where(Product_.name.as("p").eq("WHERE_VALUE")).fetch();
        check(spy.last().binds().equals(Map.of(1,"ON_VALUE",2,"WHERE_VALUE")),"join ON parameters are bound before WHERE and LIMIT");
        try {
            db.select(Product.class).as("p")
                .join(User.class,"u",Product_.id.as("p").sameAs(User_.id.as("u"))).limit(2).fetch();
            throw new AssertionError("Paginated join accepted");
        } catch(IllegalArgumentException expected) {
            check(expected.getMessage().contains("Paginating JOIN"),"join pagination must fail closed");
        }
        try {
            db.select(Product.class).as("p").join(User.class,"u",Product_.name.as("p").eq("Pencil")).fetch();
            throw new AssertionError("Cartesian join accepted");
        } catch(IllegalArgumentException expected) {
            check(expected.getMessage().contains("cartesian"),"cartesian join rejected");
        }
        AutoUuidPojo freshPojo=new AutoUuidPojo();freshPojo.setLabel("auto-pojo");db.insert(freshPojo);
        check(freshPojo.getId()!=null && freshPojo.getId().version()==7,"@Id UUID POJO generated without flags");
        AutoUuidRecord record=new AutoUuidRecord(null,"auto-record");
        try {db.insert(record);throw new AssertionError("immutable @Id requires returning API");}
        catch(IllegalArgumentException expected){check(expected.getMessage().contains("insertReturning"),"record failure message");}
        AutoUuidRecord persisted=db.insertReturning(record);
        check(record.id()==null && persisted.id()!=null && persisted.id().version()==7,"record copy generated automatically");
        check(persisted.insert(db).id().equals(persisted.id()),"ActiveRecord returns ID-bearing immutable record");
        check(db.upsertReturning(new AutoUuidRecord(null,"upsert")).id()!=null,"record upsert generates id");
        check(new AutoUuidRecord(null,"active-upsert").upsert(db).id()!=null,
            "ActiveRecord.upsert returns materialized immutable record");
        PreparedOnce once=db.upsertReturning(new PreparedOnce(null,"one-shot"));
        check(once.id()!=null && counting.preparations.get()==1,
            "upsertReturning calls materializeInsert exactly once");
        db.tx(tx->tx.upsertReturning(new PreparedOnce(null,"transaction-one-shot")));
        check(counting.preparations.get()==2,"transaction graph upsert also materializes once");
        var batch=db.batchInsertReturning(List.of(new AutoUuidRecord(null,"B1"),new AutoUuidRecord(null,"B2")));
        check(batch.size()==2 && batch.get(0).id().version()==7 && !batch.get(0).id().equals(batch.get(1).id()),"record batch generates unique IDs");
        String textId=db.insertReturning(new AutoStringRecord(null,"string")).id();
        check(java.util.UUID.fromString(textId).version()==7,"String @Id uses UUID v7 text");
        AutoLongRecord numeric=db.insertReturning(new AutoLongRecord(null,"long"));
        check(numeric.id()==42L,"Long record receives generated DB key");
        AutoIntRecord integer=db.insertReturning(new AutoIntRecord(0,"int"));
        check(integer.id()==42,"int record receives generated DB key");
        check(db.batchInsertReturning(List.of(new AutoLongRecord(null,"L1"))).getFirst().id()==42L,"numeric record batch identities");
        var nested=db.insertReturning(new AutoParentRecord(null,"record-parent",
            List.of(new AutoChildRecord(null,null,"record-child"))));
        check(nested.id().version()==7 && nested.children().getFirst().id().version()==7 &&
            nested.children().getFirst().parentId().equals(nested.id()),"immutable graph parent and child IDs/FK assigned");
        int beforeRejectedBatch=spy.calls.size();
        try {
            db.batchInsert(List.of(new AutoParentRecord(null,"unsafe-batch",
                List.of(new AutoChildRecord(null,null,"must-not-disappear")))));
            throw new AssertionError("batchInsert must not discard graph children");
        } catch(IllegalArgumentException expected) {
            check(expected.getMessage().contains("entity graphs"),"graph batch fails loudly");
        }
        check(spy.calls.size()==beforeRejectedBatch,"rejected graph batch writes no SQL");
        Identity identity=new Identity();identity.setLabel("ok");db.insert(identity);
        check(identity.getId()==42L,"JDBC generated key populated");
        check(spy.last().sql().equals("INSERT INTO identities (label) VALUES (?)"),"generated ID omitted from INSERT");
        int before=spy.connections.get();
        int previousCommits=spy.commits.get();
        db.tx(tx->{tx.batchInsert(List.of(p,p));tx.batchUpdate(List.of(p,p));new ProductQueries_Bjorm(tx).rename("hi",spy.id);});
        check(spy.connections.get()==before+1,"batch and generated repo share transaction connection");
        check(spy.commits.get()==previousCommits+1,"batch committed");
        before=spy.calls.size();
        db.forEach(Product.class, Product_.status.eq(Status.ACTIVE), item->check(item.getName().equals("Pencil"),"forEach row"));
        check(spy.calls.size()==before+1,"forEach executed single query");
        db.scan("SELECT id, name FROM products WHERE name = ?", ps->ps.setString(1,"Pencil"),ProductSummary_BjormRowMapper.INSTANCE,
            row->check(row.name().equals("Pencil"),"native projection cursor"));
        int closedBefore=spy.closedResultSets.get(), statementsBefore=spy.closedStatements.get();
        long streamed=db.withStream(db.select(Product.class),
            rows->rows.filter(item->item.getName().equals("Pencil")).count());
        check(streamed==1,"mapped sequential Stream uses cursor without materializing all rows");
        check(spy.closedResultSets.get()==closedBefore+1 &&
              spy.closedStatements.get()==statementsBefore+1,
              "mapped Stream always closes ResultSet and PreparedStatement");
        int rolledBefore=spy.rollbacks.get();
        try {
            db.withStream(db.select(Product.class),rows->{
                rows.findFirst();
                throw new IllegalStateException("consumer failed");
            });
            throw new AssertionError("stream callback exception was swallowed");
        } catch(IllegalStateException expected) {
            check(expected.getMessage().equals("consumer failed"),"callback failure propagated");
        }
        check(spy.rollbacks.get()==rolledBefore+1 &&
              spy.closedResultSets.get()==closedBefore+2 &&
              spy.closedStatements.get()==statementsBefore+2,
              "callback failure rolls back and closes JDBC resources");
        check(spy.last().binds().get(1).equals("Pencil"),"streamed projection bind");
        // FindOne must return Optional, fetch a single row server-side, and bind filters.
        Optional<Product> first=db.findOne(Product.class,Product_.name.eq("Pencil"));
        check(first.isPresent() && first.get().getName().equals("Pencil"),"findOne maps matching entity");
        check(spy.last().sql().equals("SELECT id, name, unit_price, status, version FROM products WHERE name = ? LIMIT ?"),"findOne pushes LIMIT 1 to SQL");
        check(spy.last().binds().equals(Map.of(1,"Pencil",2,1)),"findOne binds filter and LIMIT 1");
        check(db.count(Product.class,Product_.name.eq("Pencil"))==3L,"count avoids entity materialization");
        check(spy.last().sql().equals("SELECT COUNT(*) FROM products WHERE name = ?"),"count uses single SQL aggregate");
        check(spy.last().binds().equals(Map.of(1,"Pencil")),"count predicate bound");
        check(db.exists(Product.class,Product_.name.eq("Pencil")),"exists returns true");
        check(spy.last().sql().equals("SELECT 1 FROM products WHERE name = ? LIMIT 1"),"exists does not count all rows");
        check(spy.last().binds().equals(Map.of(1,"Pencil")),"exists predicate bound");
        try {
            db.lockOne(Product.class,Product_.name.eq("Pencil"),LockMode.UPDATE);
            throw new AssertionError("row locks must require active transaction");
        } catch(IllegalStateException expected) {
            check(expected.getMessage().contains("active transaction"),"no autocommit lock");
        }
        db.tx(tx -> {
            check(tx.lockOne(Product.class,Product_.name.eq("Pencil"),LockMode.UPDATE),
                "transaction FOR UPDATE returns existing row");
            check(spy.last().sql().equals("SELECT id FROM products WHERE name = ? LIMIT 1 FOR UPDATE"),
                "mapped row lock SQL");
            check(spy.last().binds().equals(Map.of(1,"Pencil")),"row lock binds typed predicate");
            check(tx.lockOne(Product.class,Product_.name.eq("Pencil"),LockMode.SHARE),
                "transaction FOR SHARE returns existing row");
            check(spy.last().sql().endsWith("LIMIT 1 FOR SHARE"),"shared row lock SQL");
        });
        spy.noRows=true;
        check(!db.exists(Product.class,Product_.name.eq("missing")),"exists returns false for empty result");
        check(db.findOne(Product.class,Product_.name.eq("missing")).isEmpty(),"findOne returns empty Optional");
        check(db.findOneFields(Product.class,Product_.name.eq("missing"),"name").isEmpty(),"findOneFields no row");
        spy.noRows=false;

        // Explicit Java properties, including @Column renaming, must restrict SQL columns.
        var projected=db.findFields(Product.class,Product_.name.eq("Pencil"),"name","price","status");
        check(projected.size()==1,"findFields returns results");
        check(projected.getFirst().get("price").equals(new BigDecimal("12.50")),"findFields reads @Column renamed property");
        check(projected.getFirst().get("status")==Status.ACTIVE,"findFields generated enum reader");
        check(spy.last().sql().equals("SELECT name, unit_price, status FROM products WHERE name = ?"),"findFields generates column-restricted SQL");
        check(spy.last().binds().equals(Map.of(1,"Pencil")),"findFields binds filter");
        List<BigDecimal> prices=db.findColumn(Product.class,"price",BigDecimal.class,Product_.name.eq("Pencil"));
        check(prices.equals(List.of(new BigDecimal("12.50"))),"findColumn reads scalar using generated mapper");
        check(spy.last().sql().equals("SELECT unit_price FROM products WHERE name = ?"),"findColumn selects only requested column");
        check(db.findColumn(Product.class,"version",int.class).equals(List.of(0)),
            "primitive int.class is boxed for scalarList");
        check(db.findColumnOne(Product.class,"version",int.class).orElseThrow()==0,
            "primitive int.class is boxed for scalarOne");
        check(db.findColumnOne(Product.class,"name",String.class,Product_.name.eq("Pencil")).orElseThrow().equals("Pencil"),"findColumnOne typed Optional");
        check(spy.last().sql().equals("SELECT name FROM products WHERE name = ? LIMIT ?"),"findColumnOne limits at database");
        check(db.select(Product.class).fields("name").values(String.class).equals(List.of("Pencil")),"DSL values without Map");
        check(db.select(Product.class).fields("name").firstValue(String.class).orElseThrow().equals("Pencil"),"DSL firstValue without Map");

        var nullColumn=db.findOneFields(Product.class,Product_.name.eq("Pencil"),"status").orElseThrow();
        check(nullColumn.containsKey("status") && nullColumn.get("status")==null,"selected SQL NULL survives in property Map");

        var byDsl=db.select(Product.class).as("p").fields("name","price")
            .where(Product_.name.as("p").eq("Pencil")).orderBy(Product_.price.as("p").desc()).limit(2).fetch();
        check(byDsl.getFirst().get("name").equals("Pencil"),"DSL named properties");
        check(spy.last().sql().equals("SELECT p.name, p.unit_price FROM products p WHERE p.name = ? ORDER BY p.unit_price DESC LIMIT ?"),"qualified projected SQL");
        check(spy.last().binds().equals(Map.of(1,"Pencil",2,2)),"qualified projected bind order");

        var projectedDto=db.select(Product.class).fields("id","name")
            .fetch(ProductSummary_BjormRowMapper.INSTANCE);
        check(projectedDto.size()==1&&projectedDto.getFirst().name().equals("Pencil"),"generated record projection without Maps");
        check(spy.last().sql().equals("SELECT id, name FROM products"),"record projection selects exactly two columns");
        check(db.select(Product.class).fields("id","name").first(ProductSummary_BjormRowMapper.INSTANCE).isPresent(),"first typed record projection");
        check(db.select(Product.class).where(Product_.name.eq("Pencil")).first().isPresent(),"DSL first entity");
        int beforeCache=spy.calls.size();
        db.tx(tx -> {
            Product cached=tx.find(Product.class,spy.id);
            Product same=tx.find(Product.class,spy.id);
            check(cached==same,"same transaction find returns same Java object");
            check(spy.calls.size()==beforeCache+1,"second find uses no SQL");
            tx.update(cached);
            Product refreshed=tx.find(Product.class,spy.id);
            check(refreshed!=cached,"write invalidates transaction first-level cache");
            check(spy.calls.size()==beforeCache+3,"UPDATE and reload execute one SQL each");
            tx.execute("UPDATE products SET name=? WHERE id=?",tx.params("native",spy.id));
            Product afterNative=tx.find(Product.class,spy.id);
            check(afterNative!=refreshed,"opaque native SQL invalidates cached objects");
        });
        db.tx(tx -> {
            Product inNewTx=tx.find(Product.class,spy.id);
            check(inNewTx!=null,"new transaction loads entity again");
        });
        int startConns=spy.connections.get();
        db.tx(tx -> {
            check(tx.count(Product.class,Product_.name.eq("Pencil"))==3L,"count stays in transaction");
            check(tx.exists(Product.class,Product_.name.eq("Pencil")),"exists stays in transaction");
            check(tx.findOne(Product.class,Product_.name.eq("Pencil")).isPresent(),"findOne in transaction");
            check(tx.findFields(Product.class,Product_.name.eq("Pencil"),"name").size()==1,"findFields in transaction");
        });
        check(spy.connections.get()==startConns+1,"select projections respect tx connection");
        for(String bad:new String[]{"notAProperty","name; DROP TABLE products"}){
            try{db.findFields(Product.class,bad);throw new AssertionError("Expected mapped-property validation");}
            catch(IllegalArgumentException expected){check(expected.getMessage().contains("Unknown mapped property"),"mapped property validated");}
        }
        try{db.findFields(Product.class,"name","name");throw new AssertionError("duplicate fields must fail");}
        catch(IllegalArgumentException expected){}
        try{db.select(Product.class).fields();throw new AssertionError("empty fields must fail");}
        catch(IllegalArgumentException expected){}

        var joinOn = Product_.id.as("p").sameAs(User_.id.as("u"));
        db.select(Product.class).as("p").join(User.class,"u").on(joinOn).fetch();
        check(spy.last().sql().contains(" INNER JOIN users u ON p.id = u.id"),"QueryDSL INNER JOIN");
        db.select(Product.class).as("p").leftJoin(User.class,"u").on(joinOn).fetch();
        check(spy.last().sql().contains(" LEFT JOIN users u ON p.id = u.id"),"QueryDSL LEFT JOIN");
        db.select(Product.class).as("p").rightJoin(User.class,"u").on(joinOn).fetch();
        check(spy.last().sql().contains(" RIGHT JOIN users u ON p.id = u.id"),"QueryDSL RIGHT JOIN");
        db.select(Product.class).as("p").fullJoin(User.class,"u").on(joinOn).fetch();
        check(spy.last().sql().contains(" FULL OUTER JOIN users u ON p.id = u.id"),"QueryDSL FULL OUTER");
        db.select(Product.class).as("p").fields("name").fullOuterJoin(User.class,"u").on(joinOn).fetch();
        check(spy.last().sql().contains(" FULL OUTER JOIN users u ON p.id = u.id"),"projected FULL OUTER");
        check(ItOrder_BjormMapper.INSTANCE.children().getFirst().joinType()==JoinType.LEFT,"LEFT default");
        check(ItOrderInner_BjormMapper.INSTANCE.children().getFirst().joinType()==JoinType.INNER,"annotated INNER");
        check(ItOrderOuter_BjormMapper.INSTANCE.children().get(0).joinType()==JoinType.RIGHT,"annotated RIGHT");
        check(ItOrderOuter_BjormMapper.INSTANCE.children().get(1).joinType()==JoinType.FULL,"annotated FULL");
        EnumMap<SqlOperation,AtomicInteger> events=new EnumMap<>(SqlOperation.class);
        for(SqlOperation kind:SqlOperation.values())events.put(kind,new AtomicInteger());
        Bjorm observed=db.observeSql((kind,durationNanos,success)->{
            check(durationNanos>=0,"JDBC duration must be nonnegative");
            check(success,"test spy JDBC operation succeeds");
            events.get(kind).incrementAndGet();
        });
        observed.count(Product.class,null);
        observed.execute("UPDATE products SET name=? WHERE id=?",observed.params("metrics",spy.id));
        observed.batchInsert(List.of(new AutoUuidRecord(UUID.randomUUID(),"metrics-1"),
            new AutoUuidRecord(UUID.randomUUID(),"metrics-2")));
        check(events.get(SqlOperation.QUERY).get()>0,"observer sees SELECT execution");
        check(events.get(SqlOperation.UPDATE).get()>0,"observer sees UPDATE execution");
        check(events.get(SqlOperation.BATCH).get()>0,"observer sees executeBatch");
        check(db.execute("UPDATE products SET name=? WHERE id=?",db.params("unobserved",spy.id))==1,
            "unobserved BJORM stays fully functional");
        check(db.observeSql((kind,nanos,success)->{
            throw new IllegalStateException("metrics exporter unavailable");
        }).execute("UPDATE products SET name=? WHERE id=?",db.params("safe",spy.id))==1,
            "broken metrics export must never alter JDBC success");
        System.out.println("PASS: POJO, projection, findOne, selected properties, @Query, DSL, locking, generated IDs, batch and transactions");
    }
}
