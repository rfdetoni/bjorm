package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

/** Verify one statement, LEFT JOIN, UNION ALL, dedup and NULL child slots. */
public final class GraphQuerySmokeTest {
    private static final UUID ROOT=UUID.fromString("0199aaaa-1111-7000-8000-000000000001");
    private static final UUID LINE_A=UUID.fromString("0199aaaa-1111-7000-8000-000000000002");
    private static final UUID LINE_B=UUID.fromString("0199aaaa-1111-7000-8000-000000000003");
    private static final UUID NOTE=UUID.fromString("0199aaaa-1111-7000-8000-000000000004");
    private static Object proxy(Class<?> type,InvocationHandler h){return Proxy.newProxyInstance(GraphQuerySmokeTest.class.getClassLoader(),new Class<?>[]{type},h);}
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        List<String> sqls=new ArrayList<>();
        Object[][] values={
          {ROOT,"Example",0,LINE_A,ROOT,"A",null,null,null,null},
          {ROOT,"Example",0,LINE_B,ROOT,"B",null,null,null,null},
          {ROOT,"Example",1,null,null,null,NOTE,ROOT,"note",0}
        };
        DataSource ds=(DataSource)proxy(DataSource.class,(x,m,a)-> {
            if(!m.getName().equals("getConnection"))throw new UnsupportedOperationException(m.getName());
            return proxy(Connection.class,(c,cm,ca)->switch(cm.getName()) {
                case "prepareStatement" -> {
                    String sql=(String)ca[0];sqls.add(sql);
                    check(sql.startsWith("WITH roots AS ("),"root CTE");
                    check(sql.contains(" LEFT JOIN bjorm_it_order_lines ") && sql.contains(" LEFT JOIN bjorm_it_order_notes "),"eager child joins");
                    check(sql.contains(" UNION ALL "),"sibling relations without cartesian multiplication");
                    yield proxy(PreparedStatement.class,(ps,pm,pa)->switch(pm.getName()){
                        case "setFetchSize","setQueryTimeout","setMaxRows","setObject","setInt","setLong","close" -> null;
                        case "executeQuery" -> proxy(ResultSet.class,new InvocationHandler(){int row=-1;boolean wasNull;
                            public Object invoke(Object rs,Method rm,Object[] ra){return switch(rm.getName()){
                                case "next" -> ++row<values.length;
                                case "getObject","getString","getInt" -> {Object v=values[row][(Integer)ra[0]-1];wasNull=v==null;yield rm.getName().equals("getInt")?(v==null?0:((Number)v).intValue()):v;}
                                case "wasNull" -> wasNull;
                                case "close" -> null;
                                default -> throw new UnsupportedOperationException(rm.getName());
                            };}
                        });
                        default -> throw new UnsupportedOperationException(pm.getName());
                    });
                }
                case "close" -> null;
                default -> throw new UnsupportedOperationException(cm.getName());
            });
        });
        Bjorm db=Bjorm.open(ds,ItOrder_BjormMapper.INSTANCE,ItOrderLine_BjormMapper.INSTANCE,ItOrderNote_BjormMapper.INSTANCE);
        ItOrder order=db.find(ItOrder.class,ROOT);
        check(order!=null && order.getLines().size()==2 && order.getNotes().size()==1,"complete graph from one SELECT");
        check(order.getLines().getFirst().getSku().equals("A") && order.getNotes().getFirst().getNote().equals("note"),"typed children mapped");
        check(sqls.size()==1,"no query per child/parent");
        try {
            Bjorm limited=Bjorm.open(ds,new BjormOptions(30,2,128),
                ItOrder_BjormMapper.INSTANCE,ItOrderLine_BjormMapper.INSTANCE,ItOrderNote_BjormMapper.INSTANCE);
            limited.find(ItOrder.class,ROOT);
            throw new AssertionError("Expected bounded materialization failure");
        } catch(IllegalStateException expected) { check(expected.getMessage().contains("maxBufferedRows"),"bounded graph reads"); }
        int before=sqls.size();
        var filtered=db.select(ItOrder.class).as("o")
            .join(ItOrderLine.class,"l").on(ItOrder_.id.as("o").sameAs(ItOrderLine_.orderId.as("l")))
            .fetch();
        check(filtered.size()==1 && filtered.getFirst().getLines().size()==2,
            "QueryDSL explicit JOIN and children use one SQL");
        check(sqls.size()==before+1 && sqls.getLast().contains("SELECT DISTINCT * FROM") &&
            sqls.getLast().contains(" INNER JOIN bjorm_it_order_lines l"),
            "matched roots deduplicated before expanding child collections");
        try {
            db.select(ItOrder.class).as("o").rightJoin(ItOrderLine.class,"l")
                .on(ItOrder_.id.as("o").sameAs(ItOrderLine_.orderId.as("l"))).limit(1).fetch();
            throw new AssertionError("Expected paginated explicit JOIN guard");
        } catch(IllegalArgumentException expected) {
            check(expected.getMessage().contains("Paginating JOIN"),"unsafe joined pagination rejected");
        }
        System.out.println("PASS: eager joined entity graph, sibling UNION ALL, one SQL and bounded reads");
    }
}
