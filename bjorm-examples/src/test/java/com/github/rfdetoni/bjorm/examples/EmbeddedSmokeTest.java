package com.github.rfdetoni.bjorm.examples;

import java.util.UUID;

public final class EmbeddedSmokeTest {
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    public static void main(String[] args) {
        var recordMapper=ItEmbeddedRecord_BjormMapper.INSTANCE;
        check(recordMapper.columnCount()==4,"embedded value expands into two scalar columns");
        check(recordMapper.insertSql().contains("(id, label, shipping_street, shipping_postal)"),
            "record insert SQL flattens named prefixed value columns");
        check(recordMapper.columnFor("address.street").equals("shipping_street") &&
              recordMapper.columnFor("address.zipCode").equals("shipping_postal"),
            "nested Java property names are statically mapped to SQL identifiers");
        check(ItEmbeddedRecord_.address_street!=null &&
              ItEmbeddedRecord_.address_zipCode!=null,
            "type-safe nested paths are generated");
        UUID id=UUID.randomUUID();
        ItAddress value=new ItAddress("Avenue","89201");
        ItEmbeddedRecord filled=recordMapper.materializeInsert(new ItEmbeddedRecord(null,"R",value));
        check(filled.id()!=null && filled.address().equals(value),"UUIDv7 record copy preserves embedded value");
        check(ItEmbeddedPojo_BjormMapper.INSTANCE.columnFor("billing.zipCode").equals("billing_postal"),
            "POJO embedded value uses default member prefix");
        check(ItEmbeddedPojo_BjormMapper.INSTANCE.updateSql().contains("billing_postal = ?"),
            "POJO embedded update includes flattened scalar property");
        System.out.println("PASS: @Embedded flattening, prefixed SQL, POJO and record paths");
    }
}
