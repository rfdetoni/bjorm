package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
public final class InheritedFieldSmokeTest {
    public static void main(String[] args)throws Exception {
        var mapper=InheritedPatient_BjormMapper.INSTANCE;
        if(!mapper.selectAllSql().equals("SELECT id, created_at, name FROM bjorm_it_inherited"))
            throw new AssertionError("Inherited columns missing: "+mapper.selectAllSql());
        InheritedPatient patient=new InheritedPatient();
        patient.setName("Example");patient.setCreatedAt(LocalDateTime.of(2026,10,8,10,0));
        mapper.materializeInsert(patient);
        if(patient.getId()==null || patient.getId().version()!=7)
            throw new AssertionError("Inherited UUID v7 ID not generated");
        Map<Integer,Object> binds=new HashMap<>();
        PreparedStatement ps=(PreparedStatement)java.lang.reflect.Proxy.newProxyInstance(
            InheritedFieldSmokeTest.class.getClassLoader(),new Class[]{PreparedStatement.class},
            (proxy,method,values)->{
                if(method.getName().startsWith("set")){binds.put((Integer)values[0],values[1]);return null;}
                throw new UnsupportedOperationException(method.getName());
            });
        mapper.bindInsert(ps,patient,SqlDialects.POSTGRESQL);
        if(!patient.getId().equals(binds.get(1)) || !patient.getCreatedAt().equals(binds.get(2)) ||
           !"Example".equals(binds.get(3)))
            throw new AssertionError("Inherited binding order mismatch: "+binds);
        if(!mapper.columnFor("createdAt").equals("created_at"))
            throw new AssertionError("Inherited @Column not mapped");
        System.out.println("PASS: inherited @Id and @Column with deterministic bind order");
    }
}
