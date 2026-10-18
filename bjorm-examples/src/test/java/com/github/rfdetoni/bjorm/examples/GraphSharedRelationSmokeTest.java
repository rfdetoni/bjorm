package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

/** Same generated ChildRelation reused under two parent branches must hydrate independently. */
public final class GraphSharedRelationSmokeTest {
    private static Object proxy(Class<?> type,InvocationHandler h){
        return Proxy.newProxyInstance(GraphSharedRelationSmokeTest.class.getClassLoader(),new Class<?>[]{type},h);
    }
    private static void check(boolean valid,String reason){if(!valid)throw new AssertionError(reason);}
    public static void main(String[] args){
        UUID root=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();
        UUID firstLeaf=UUID.randomUUID(),secondLeaf=UUID.randomUUID();
        // root(2), branch index(1), primary branch(3), primary leaf(3),
        // backup branch(3), backup leaf(3).
        Object[][] rows={
            {root,"root",0,first,root,null,firstLeaf,first,"primary",null,null,null,null,null,null},
            {root,"root",1,null,null,null,null,null,null,second,null,root,secondLeaf,second,"backup"}
        };
        DataSource source=(DataSource)proxy(DataSource.class,(ds,m,a)->{
            if(!m.getName().equals("getConnection"))throw new UnsupportedOperationException(m.getName());
            return proxy(Connection.class,(c,cm,ca)->switch(cm.getName()){
                case "prepareStatement" -> proxy(PreparedStatement.class,(ps,pm,pa)->switch(pm.getName()){
                    case "executeQuery" -> proxy(ResultSet.class,new InvocationHandler(){
                        int row=-1;boolean nullColumn;
                        public Object invoke(Object rs,Method rm,Object[] ra){
                            return switch(rm.getName()){
                                case "next" -> ++row<rows.length;
                                case "getObject","getString","getInt" -> {
                                    Object v=rows[row][(Integer)ra[0]-1];
                                    nullColumn=v==null;
                                    yield rm.getName().equals("getInt")?(v==null?0:((Number)v).intValue()):v;
                                }
                                case "wasNull" -> nullColumn;
                                case "close" -> null;
                                default -> throw new UnsupportedOperationException(rm.getName());
                            };
                        }
                    });
                    case "setQueryTimeout","setMaxRows","setFetchSize","setObject","setInt","setLong","close" -> null;
                    default -> throw new UnsupportedOperationException(pm.getName());
                });
                case "close" -> null;
                default -> throw new UnsupportedOperationException(cm.getName());
            });
        });
        Bjorm db=Bjorm.open(source,ItSharedRoot_BjormMapper.INSTANCE,
            ItSharedBranch_BjormMapper.INSTANCE,ItSharedLeaf_BjormMapper.INSTANCE);
        ItSharedRoot graph=db.find(ItSharedRoot.class,root);
        check(graph!=null && graph.primary().size()==1 && graph.backup().size()==1,
            "both paths must load distinct branch instances");
        check(graph.primary().getFirst().leaves().size()==1 &&
            "primary".equals(graph.primary().getFirst().leaves().getFirst().label()),
            "primary branch must hydrate its own children");
        check(graph.backup().getFirst().leaves().size()==1 &&
            "backup".equals(graph.backup().getFirst().leaves().getFirst().label()),
            "backup branch must hydrate its own children");
        try {
            db.select(ItSharedRoot.class).as("r")
                .orderBy(ItSharedLeaf_.label.as("leaf").asc()).fetch();
            throw new AssertionError("ORDER BY joined-only graph property should fail");
        }catch(IllegalArgumentException expected){
            check(expected.getMessage().contains("root columns"),"clear graph sorting error");
        }
        System.out.println("PASS: shared relation slots across paths and safe graph ORDER BY");
    }
}
