package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.SqlDialects;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
public final class OrdinalSmokeTest {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        var mapper=OrdinalEvent_BjormMapper.INSTANCE;
        UUID id=UUID.randomUUID();
        Map<Integer,Object> binds=new HashMap<>();
        PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(
            OrdinalSmokeTest.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},
            (obj,method,values)->switch(method.getName()){
                case "setObject","setString"->{binds.put((Integer)values[0],values[1]);yield null;}
                default->throw new UnsupportedOperationException(method.getName());
            });
        mapper.bindInsert(ps,new OrdinalEvent(id,Status.INACTIVE,"event"),SqlDialects.POSTGRESQL);
        check(Integer.valueOf(1).equals(binds.get(2)),"ordinal enum bind must be integer");
        Object[] row={id,1,"event"};
        ResultSet rs=(ResultSet)Proxy.newProxyInstance(OrdinalSmokeTest.class.getClassLoader(),
            new Class<?>[]{ResultSet.class},(obj,method,values)->switch(method.getName()){
                case "getObject"->row[(Integer)values[0]-1];
                case "getString"->row[(Integer)values[0]-1];
                case "wasNull"->false;
                default->throw new UnsupportedOperationException(method.getName());
            });
        check(mapper.read(rs).status()==Status.INACTIVE,"ordinal enum maps to exact enum member");
        row[1]=99;
        try {mapper.read(rs);throw new AssertionError("invalid enum ordinal accepted");}
        catch(SQLException expected) {check(expected.getMessage().contains("out of range"),"bounds check");}
        check(Integer.valueOf(0).equals(mapper.bulkParameterValue("status",Status.ACTIVE)),
            "bulk update serializes ordinal instead of enum name");
        System.out.println("PASS: generated ordinal enum mapping with validated database value");
    }
}
