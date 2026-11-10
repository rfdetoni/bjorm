package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.JoinType;
import java.util.UUID;

public final class ManyToOneSmokeTest {
    private static void check(boolean valid,String description) {
        if(!valid)throw new AssertionError(description);
    }
    public static void main(String[] args) {
        var mapper=ItInvoice_BjormMapper.INSTANCE;
        UUID customerId=UUID.randomUUID();
        UUID invoiceId=UUID.randomUUID();
        ItUser customer=new ItUser(customerId,"Alice",42);
        ItInvoice original=new ItInvoice(invoiceId,customerId,"ONE",null);
        check(mapper.columnCount()==3,"relation must not be treated as a persisted column");
        check(mapper.insertSql().contains("(id, customerId, reference)"),
            "INSERT only writes the scalar foreign key, not the object reference");
        check(mapper.parents().size()==1 &&
              mapper.parents().getFirst().targetType()==ItUser.class &&
              mapper.parents().getFirst().mappedBy().equals("customerId") &&
              mapper.parents().getFirst().joinType()==JoinType.LEFT,
            "compile-time generated left many-to-one metadata");
        ItInvoice populated=mapper.withParent(original,"customer",customer);
        check(populated!=original && populated.customer()==customer &&
              populated.id().equals(original.id()) &&
              populated.customerId().equals(original.customerId()),
            "record hydration copies immutable value preserving scalar properties");
        var inner=ItInvoiceInner_BjormMapper.INSTANCE;
        check(inner.parents().getFirst().joinType()==JoinType.INNER,
            "explicit many-to-one join policy is retained");
        ItInvoicePojo pojo=new ItInvoicePojo();
        pojo.setId(invoiceId);
        pojo.setCustomerId(customerId);
        check(ItInvoicePojo_BjormMapper.INSTANCE.withParent(pojo,"customer",customer)==pojo &&
              pojo.getCustomer()==customer,
            "POJO hydration uses generated setter without reflection");
        System.out.println("PASS: compile-time ManyToOne records, POJOs, foreign keys and join policies");
    }
}
