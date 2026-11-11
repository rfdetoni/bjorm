package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;

public final class CompositeIdSmokeTest {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args) throws java.sql.SQLException {
        UUID tenant=UUID.randomUUID();
        var mapper=ItCompositeRecord_BjormMapper.INSTANCE;
        var record=new ItCompositeRecord(tenant,"SKU-1","initial",0);
        CompositeId key=CompositeId.of(tenant,"SKU-1");
        check(mapper.compositeId(),"compile-time multi-key metadata");
        check(key.equals(mapper.id(record)),"stable ordered tuple key");
        check(mapper.id(record).equals(CompositeId.of(tenant,"SKU-1")),"tuple key structural equality");
        check(mapper.selectSql().endsWith(" WHERE tenantId = ? AND sku = ?"),
            "select matches both key parts");
        check(mapper.deleteSql().endsWith(" WHERE tenantId = ? AND sku = ? AND version = ?"),
            "versioned delete matches all key parts and version");
        check(mapper.updateSql().contains("WHERE tenantId = ? AND sku = ? AND version = ?") &&
            !mapper.updateSql().contains("SET tenantId ="),
            "UPDATE does not modify either key component");
        check(mapper.isIdProperty("tenantId")&&mapper.isIdProperty("sku") &&
            !mapper.isIdProperty("label"),"bulk write guards both key parts");
        check(ItCompositeRecord_.tenantId!=null&&ItCompositeRecord_.sku!=null,
            "typed predicate DSL for both key components");
        check(ItCompositeRecord_BjormMapper.INSTANCE.idProperties().equals(
            java.util.List.of("tenantId","sku")),"ordered composite key metadata");
        check(ItCompositeReference_BjormMapper.INSTANCE.parents().getFirst().mappedByParts().equals(
            java.util.List.of("tenantId","sku")),"compile-time ordered composite FK metadata");
        check(ItCompositePojo_BjormMapper.INSTANCE.compositeId(),"POJO tuple id generated");
        check(CompositeId.of(tenant,"SKU-2").equals(
            ItCompositePojo_BjormMapper.INSTANCE.id(poJo(tenant,"SKU-2"))),
            "POJO tuple key is read without reflection");
        try{mapper.bindId(null,1,tenant,SqlDialects.POSTGRESQL);
            throw new AssertionError("single ID accepted for composite mapper");}
        catch(IllegalArgumentException expected) {}
        System.out.println("PASS: typed composite ID mapping and multi-part optimistic SQL");
    }
    private static ItCompositePojo poJo(UUID tenant,String sku) {
        ItCompositePojo p=new ItCompositePojo();
        p.setTenantId(tenant);p.setSku(sku);p.setLabel("test");return p;
    }
}
