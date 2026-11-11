package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.sql.*;
import java.util.*;
public final class TypedJsonSmokeTest {
    public static void main(String[] args)throws Exception {
        var mapper=ItTypedJson_BjormMapper.INSTANCE;
        if(!mapper.insertSql(SqlDialects.POSTGRESQL).contains("CAST(? AS jsonb)"))
            throw new AssertionError("PostgreSQL dialect cast lost");
        if(!mapper.insertSql(SqlDialects.MYSQL).contains("CAST(? AS JSON)"))
            throw new AssertionError("MySQL dialect cast lost");
        UUID id=UUID.randomUUID();
        Map<Integer,Object> params=new HashMap<>();
        PreparedStatement ps=(PreparedStatement)java.lang.reflect.Proxy.newProxyInstance(
            TypedJsonSmokeTest.class.getClassLoader(),new Class[]{PreparedStatement.class},
            (proxy,method,values)->{
                if(method.getName().startsWith("set")){params.put((Integer)values[0],values[1]);return null;}
                throw new UnsupportedOperationException(method.getName());
            });
        mapper.bindInsert(ps,new ItTypedJson(id,List.of(1,2,3)),SqlDialects.POSTGRESQL);
        if(!"[1,2,3]".equals(params.get(2)))throw new AssertionError("Typed JSON not encoded");
        mapper.bindInsert(ps,new ItTypedJson(id,null),SqlDialects.POSTGRESQL);
        if(params.get(2)!=null)throw new AssertionError("Null JSON is not SQL NULL");
        ResultSet rs=(ResultSet)java.lang.reflect.Proxy.newProxyInstance(
            TypedJsonSmokeTest.class.getClassLoader(),new Class[]{ResultSet.class},
            (proxy,method,values)->switch(method.getName()){
                case "getObject" -> id;
                case "getString" -> "[4,5]";
                default -> throw new UnsupportedOperationException(method.getName());
            });
        if(!mapper.readAt(rs,1).values().equals(List.of(4,5)))
            throw new AssertionError("Typed JSON record not decoded");
        if(!mapper.readProperty(rs,2,"values").equals(List.of(4,5)))
            throw new AssertionError("Typed JSON property projection not decoded");
        ItTypedJsonPojo mutableSource=new ItTypedJsonPojo();
        List<Integer> mutableValues=new ArrayList<>(List.of(1,2,3));
        mutableSource.setValues(mutableValues);
        try {
            ItTypedJsonPojo_BjormMapper.INSTANCE.copyForWrite(mutableSource);
            throw new AssertionError("Detached POJO copy must reject mutable typed JSON");
        } catch(UnsupportedOperationException expected) {}
        if(mutableSource.getValues()!=mutableValues ||
            !mutableValues.equals(List.of(1,2,3)) || mutableSource.getId()!=null)
            throw new AssertionError("Unsafe detached copy changed the caller's JSON value");
        System.out.println("PASS: dialect-aware typed JSON binding, decoding and detached safety");
    }
}
