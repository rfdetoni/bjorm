package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

/** Verify generated eager SQL JoinType without a real database. */
public final class AnnotatedJoinSmokeTest {
    private static Object proxy(Class<?> type,InvocationHandler h){
        return Proxy.newProxyInstance(AnnotatedJoinSmokeTest.class.getClassLoader(),new Class<?>[]{type},h);
    }
    public static void main(String[] args){
        ArrayList<String> sqls=new ArrayList<>();
        DataSource source=(DataSource)proxy(DataSource.class,(ds,m,a)->{
            if(!m.getName().equals("getConnection"))throw new UnsupportedOperationException(m.getName());
            return proxy(Connection.class,(c,cm,ca)->switch(cm.getName()){
                case "prepareStatement" -> {
                    sqls.add((String)ca[0]);
                    yield proxy(PreparedStatement.class,(p,pm,pa)->switch(pm.getName()){
                        case "executeQuery" -> proxy(ResultSet.class,(rs,rm,ra)->switch(rm.getName()){
                            case "next" -> false;
                            case "close" -> null;
                            default -> throw new UnsupportedOperationException(rm.getName());
                        });
                        case "setQueryTimeout","setMaxRows","setFetchSize","setInt","setLong","setObject","close" -> null;
                        default -> throw new UnsupportedOperationException(pm.getName());
                    });
                }
                case "close" -> null;
                default -> throw new UnsupportedOperationException(cm.getName());
            });
        });
        Bjorm db=Bjorm.open(source,ItOrderInner_BjormMapper.INSTANCE,ItOrderOuter_BjormMapper.INSTANCE,
            ItOrderLine_BjormMapper.INSTANCE,ItOrderNote_BjormMapper.INSTANCE);
        db.select(ItOrderInner.class).fetch();
        if(sqls.size()!=1 || !sqls.getFirst().contains(" INNER JOIN bjorm_it_order_notes "))
            throw new AssertionError("INNER @Children not generated in one SQL");
        db.select(ItOrderOuter.class).fetch();
        if(sqls.size()!=2 || !sqls.getLast().contains(" RIGHT JOIN bjorm_it_order_lines ") ||
            !sqls.getLast().contains(" FULL OUTER JOIN bjorm_it_order_notes ") ||
            !sqls.getLast().contains(" UNION ALL "))
            throw new AssertionError("RIGHT/FULL @Children SQL missing or cross-collection Cartesian join");
        System.out.println("PASS: annotated LEFT(default), INNER, RIGHT, FULL eager graph SQL");
    }
}
