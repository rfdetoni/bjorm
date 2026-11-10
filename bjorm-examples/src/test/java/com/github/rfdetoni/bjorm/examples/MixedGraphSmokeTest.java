package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.JoinType;

public final class MixedGraphSmokeTest {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args) {
        var mapper=ItOrderWithCustomer_BjormMapper.INSTANCE;
        check(mapper.children().size()==1,"combined mapper exposes a collection relation");
        check(mapper.parents().size()==1,"combined mapper exposes a reference relation");
        check(mapper.parents().getFirst().targetType()==ItUser.class &&
              mapper.parents().getFirst().joinType()==JoinType.LEFT,
            "to-one target and join type generated");
        check(mapper.children().getFirst().childType()==ItOrderLine.class,
            "to-many child type generated");
        ItOrderWithCustomer value=new ItOrderWithCustomer();
        ItUser customer=new ItUser(java.util.UUID.randomUUID(),"First",11);
        check(mapper.withParent(value,"customer",customer)==value &&
              value.getCustomer()==customer,"record reference assigned by POJO setter");
        check(mapper.withChildren(value,"lines",java.util.List.of())==value,
            "collection setter works after to-one assignment");
        System.out.println("PASS: mixed eager graph mapper exposes both association types");
    }
}
