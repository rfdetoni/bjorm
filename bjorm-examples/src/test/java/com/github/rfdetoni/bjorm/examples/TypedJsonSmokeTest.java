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
        // A record is only shallow-immutable: a typed JSON List remains mutable.
        List<Integer> mutableRecordValue=new ArrayList<>(List.of(1,2,3));
        ItTypedJson typedRecord=new ItTypedJson(UUID.randomUUID(),mutableRecordValue);
        try {
            mapper.copyForWrite(typedRecord);
            throw new AssertionError("Detached record must reject mutable JSON without a deep-copy contract");
        } catch(UnsupportedOperationException expected) {}
        if(typedRecord.values()!=mutableRecordValue || !mutableRecordValue.equals(List.of(1,2,3)))
            throw new AssertionError("Detached rejection must not change caller-owned record payload");
        try {
            ItConvertedRecord_BjormMapper.INSTANCE.copyForWrite(
                new ItConvertedRecord(UUID.randomUUID(),
                    new MoneyValue(new java.math.BigDecimal("42.00")),"immutable"));
            throw new AssertionError("Detached record must reject conversion without a verified deep-copy contract");
        } catch(UnsupportedOperationException expected) {}
        SqlPredicate pgPath=SqlJsonPredicates.textEquals(
            new Field<String>("payload"),SqlDialects.POSTGRESQL,
            List.of("meta","status"),"ready");
        if(pgPath.sql().contains("meta") || pgPath.sql().contains("status") ||
            !pgPath.params().equals(List.of("meta","status","ready")))
            throw new AssertionError("PostgreSQL JSON path/value must be bound, not interpolated");
        SqlPredicate mysqlPath=SqlJsonPredicates.textEquals(
            new Field<String>("payload"),SqlDialects.MYSQL,
            List.of("meta","status"),"ready");
        if(mysqlPath.sql().contains("$.meta.status") ||
            !mysqlPath.params().equals(List.of("$.meta.status","ready")))
            throw new AssertionError("MySQL JSON path must be a bound value");
        try {
            SqlJsonPredicates.textEquals(new Field<String>("payload"),SqlDialects.POSTGRESQL,
                List.of("meta); DROP TABLE users; --"),"anything");
            throw new AssertionError("Unsafe JSON path segment was accepted");
        } catch(IllegalArgumentException expected) {}
        System.out.println("PASS: dialect-aware JSON binding, decoding and detached record/POJO safety");
    }
}
