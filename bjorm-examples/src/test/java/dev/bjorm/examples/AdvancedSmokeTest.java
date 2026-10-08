package dev.bjorm.examples;

import dev.bjorm.*;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Exercises generated repositories, POJO, projections, optimistic locking, batch and keys without external libraries. */
public final class AdvancedSmokeTest {
    private record Call(int connection,String sql,Map<Integer,Object> binds) {}
    private static void check(boolean condition,String description){if(!condition)throw new AssertionError(description);}
    private static Object proxy(Class<?> type,InvocationHandler handler){return Proxy.newProxyInstance(AdvancedSmokeTest.class.getClassLoader(),new Class<?>[]{type},handler);}
    private static final class JdbcSpy {
        final List<Call> calls=new ArrayList<>();
        final AtomicInteger connections=new AtomicInteger(),commits=new AtomicInteger(),rollbacks=new AtomicInteger();
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
                case "setInt","setLong","setObject","setString","setBoolean","setDouble","setFloat","setShort"->{binds.put((Integer)a[0],a[1]);yield null;}
                case "addBatch"->{batch.add(new HashMap<>(binds));yield null;}
                case "executeBatch"->{int[] counts=new int[batch.size()];Arrays.fill(counts,updateResult);for(Map<Integer,Object> b:batch)calls.add(new Call(connection,sql,Map.copyOf(b)));batch.clear();yield counts;}
                case "executeUpdate"->{calls.add(new Call(connection,sql,Map.copyOf(binds)));yield updateResult;}
                case "executeQuery"->{calls.add(new Call(connection,sql,Map.copyOf(binds)));
                    if(noRows)yield result(null);
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
                case "close","clearParameters","setFetchSize"->null;
                default->throw new UnsupportedOperationException("PreparedStatement."+m.getName());
            });
        }
        ResultSet result(Object[] row){
            final int[] cursor={-1};final boolean[] wasNull={false};
            return (ResultSet)proxy(ResultSet.class,(p,m,a)->switch(m.getName()){
                case "next"->row!=null && ++cursor[0]==0;
                case "close"->null;
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
        Bjorm db=Bjorm.open(spy.dataSource(),Product_BjormMapper.INSTANCE,Identity_BjormMapper.INSTANCE,User_BjormMapper.INSTANCE,JsonDocument_BjormMapper.INSTANCE);
        Bjorm discovered=Bjorm.open(spy.dataSource());
        check(discovered.find(Product.class,spy.id)!=null,"generated mapper service discovery");
        Product p=new Product();p.setId(spy.id);p.setName("Pencil");p.setPrice(new BigDecimal("12.50"));p.setStatus(Status.ACTIVE);
        p.insert(db);
        check(spy.last().sql().startsWith("INSERT INTO products"),"POJO mapper INSERT");
        check(spy.last().binds().get(4).equals("ACTIVE"),"enum binds as name");
        Product loaded=db.find(Product.class,spy.id);
        check(loaded.getStatus()==Status.ACTIVE&&loaded.getPrice().compareTo(p.getPrice())==0,"POJO generated row mapper");
        db.update(p);
        check(spy.last().sql().equals("UPDATE products SET name = ?, unit_price = ?, status = ?, version = version + 1 WHERE id = ? AND version = ?"),"optimistic UPDATE shape");
        check(spy.last().binds().get(5).equals(0),"optimistic version binder");
        spy.updateResult=0;
        try{db.tx(tx->tx.update(p));throw new AssertionError("must detect stale version");}
        catch(OptimisticLockException expected){};
        check(spy.rollbacks.get()==1,"optimistic locking rolls transaction back");
        spy.updateResult=1;
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
        check(spy.last().binds().get(5).equals(10)&&spy.last().binds().get(6).equals(5),"pagination values parameterized");
        check(Product_.name.in(List.of()).sql().equals("1 = 0"),"empty IN is false");
        db.select(Product.class).as("p")
            .join(User.class,"u", Product_.id.as("p").sameAs(User_.id.as("u")))
            .where(Product_.name.as("p").eq("Pencil"))
            .orderBy(Product_.name.as("p").asc()).limit(3).fetch();
        check(spy.last().sql().equals("SELECT p.id, p.name, p.unit_price, p.status, p.version FROM products p INNER JOIN users u ON p.id = u.id WHERE p.name = ? ORDER BY p.name ASC LIMIT ?"),"typed join compilation");
        check(spy.last().binds().equals(Map.of(1,"Pencil",2,3)),"typed join bind ordering");
        db.select(Product.class).as("p")
            .join(User.class,"u",Product_.id.as("p").sameAs(User_.id.as("u"))
                .and(Product_.name.as("p").eq("ON_VALUE")))
            .where(Product_.name.as("p").eq("WHERE_VALUE")).limit(4).fetch();
        check(spy.last().binds().equals(Map.of(1,"ON_VALUE",2,"WHERE_VALUE",3,4)),"join ON parameters are bound before WHERE and LIMIT");
        Identity identity=new Identity();identity.setLabel("ok");db.insert(identity);
        check(identity.getId()==42L,"JDBC generated key populated");
        check(spy.last().sql().equals("INSERT INTO identities (label) VALUES (?)"),"generated ID omitted from INSERT");
        int before=spy.connections.get();
        db.tx(tx->{tx.batchInsert(List.of(p,p));tx.batchUpdate(List.of(p,p));new ProductQueries_Bjorm(tx).rename("hi",spy.id);});
        check(spy.connections.get()==before+1,"batch and generated repo share transaction connection");
        check(spy.commits.get()==1,"batch committed");
        before=spy.calls.size();
        db.forEach(Product.class, Product_.status.eq(Status.ACTIVE), item->check(item.getName().equals("Pencil"),"forEach row"));
        check(spy.calls.size()==before+1,"forEach executed single query");
        db.scan("SELECT id, name FROM products WHERE name = ?", ps->ps.setString(1,"Pencil"),ProductSummary_BjormRowMapper.INSTANCE,
            row->check(row.name().equals("Pencil"),"native projection cursor"));
        check(spy.last().binds().get(1).equals("Pencil"),"streamed projection bind");
        // FindOne must return Optional, fetch a single row server-side, and bind filters.
        Optional<Product> first=db.findOne(Product.class,Product_.name.eq("Pencil"));
        check(first.isPresent() && first.get().getName().equals("Pencil"),"findOne maps matching entity");
        check(spy.last().sql().equals("SELECT id, name, unit_price, status, version FROM products WHERE name = ? LIMIT ?"),"findOne pushes LIMIT 1 to SQL");
        check(spy.last().binds().equals(Map.of(1,"Pencil",2,1)),"findOne binds filter and LIMIT 1");
        spy.noRows=true;
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
        int startConns=spy.connections.get();
        db.tx(tx -> {
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

        System.out.println("PASS: POJO, projection, findOne, selected properties, @Query, DSL, locking, generated IDs, batch and transactions");
    }
}
