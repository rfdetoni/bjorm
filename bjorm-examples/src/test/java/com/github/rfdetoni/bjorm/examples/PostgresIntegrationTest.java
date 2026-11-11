package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.*;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/** Opt-in end-to-end JDBC integration. Requires a local PostgreSQL database and driver on the classpath. */
public final class PostgresIntegrationTest {
    private static void check(boolean condition,String description){if(!condition)throw new AssertionError(description);}
    public static void main(String[] ignored)throws Exception {
        String url=System.getenv("BJORM_TEST_JDBC_URL");
        if(url==null||url.isBlank()){System.out.println("SKIP: BJORM_TEST_JDBC_URL not configured");return;}
        String user=System.getenv().getOrDefault("BJORM_TEST_USER","");
        String password=System.getenv().getOrDefault("BJORM_TEST_PASSWORD","");
        DataSource ds=new DataSource(){
            public Connection getConnection()throws SQLException{return DriverManager.getConnection(url,user,password);}
            public Connection getConnection(String u,String p)throws SQLException{return DriverManager.getConnection(url,u,p);}
            public PrintWriter getLogWriter(){return null;}
            public void setLogWriter(PrintWriter w){}
            public void setLoginTimeout(int t){}
            public int getLoginTimeout(){return 0;}
            public Logger getParentLogger(){return Logger.getGlobal();}
            public <T>T unwrap(Class<T> c)throws SQLException{throw new SQLException("unsupported");}
            public boolean isWrapperFor(Class<?> c){return false;}
        };
        // Use an expendable test database. Never run against an existing schema with fixture table names.
        try(Connection c=ds.getConnection();Statement st=c.createStatement()){
            st.execute("CREATE TABLE bjorm_it_users (id uuid PRIMARY KEY, name text NOT NULL, age integer NOT NULL)");
            st.execute("CREATE TABLE bjorm_it_invoices (id uuid PRIMARY KEY, customerId uuid REFERENCES bjorm_it_users(id), reference text)");
            st.execute("CREATE TABLE bjorm_it_composite (tenantId uuid NOT NULL,sku text NOT NULL,label text,version integer NOT NULL DEFAULT 0,PRIMARY KEY(tenantId,sku))");
            st.execute("CREATE TABLE bjorm_it_composite_pojo (tenantId uuid NOT NULL,sku text NOT NULL,label text,PRIMARY KEY(tenantId,sku))");
            st.execute("CREATE TABLE bjorm_it_embedded_records (id uuid PRIMARY KEY, label text, shipping_street text, shipping_postal text)");
            st.execute("CREATE TABLE bjorm_it_embedded_pojos (id uuid PRIMARY KEY, label text, billing_street text, billing_postal text)");
            st.execute("CREATE TABLE bjorm_it_converted (id uuid PRIMARY KEY, amount numeric(18,2), label text)");
            st.execute("CREATE SCHEMA bjorm_test");
            st.execute("CREATE TABLE bjorm_test.bjorm_it_static (id uuid PRIMARY KEY, label text)");
            st.execute("CREATE SCHEMA bjorm_tenant_a");
            st.execute("CREATE SCHEMA bjorm_tenant_b");
            st.execute("CREATE TABLE bjorm_tenant_a.bjorm_it_users (id uuid PRIMARY KEY, name text NOT NULL, age integer NOT NULL)");
            st.execute("CREATE TABLE bjorm_tenant_b.bjorm_it_users (id uuid PRIMARY KEY, name text NOT NULL, age integer NOT NULL)");
            st.execute("CREATE TABLE bjorm_it_instants (id uuid PRIMARY KEY, label text, occurred_at timestamptz, version integer NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE bjorm_it_products (id uuid PRIMARY KEY, name text, unit_price numeric(18,2), status text, version integer NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE bjorm_it_columns (id uuid PRIMARY KEY, read_only text NOT NULL, server_val text DEFAULT 'server-default')");
            st.execute("CREATE TABLE bjorm_it_ordinal (id uuid PRIMARY KEY, status integer, label text)");
            st.execute("CREATE TABLE bjorm_it_audit (id uuid PRIMARY KEY, label text, createdAt timestamptz, updatedAt timestamptz)");
            st.execute("CREATE TABLE bjorm_it_audit_pojo (id uuid PRIMARY KEY, label text, createdAt timestamptz, updatedAt timestamptz)");
            st.execute("CREATE TABLE bjorm_it_documents (id uuid PRIMARY KEY, payload jsonb)");
            st.execute("CREATE TABLE bjorm_it_orders (id uuid PRIMARY KEY, description text, customerId uuid REFERENCES bjorm_it_users(id))");
            // FK RESTRICT: cascade here is implemented by BJORM, not delegated to PostgreSQL.
            st.execute("CREATE TABLE bjorm_it_order_lines (id uuid PRIMARY KEY, orderId uuid NOT NULL REFERENCES bjorm_it_orders(id), sku text)");
            st.execute("CREATE TABLE bjorm_it_order_lines_to_one (id uuid PRIMARY KEY, orderId uuid NOT NULL REFERENCES bjorm_it_orders(id), userId uuid REFERENCES bjorm_it_users(id), sku text)");
            st.execute("CREATE TABLE bjorm_it_order_notes (id uuid PRIMARY KEY, orderId uuid NOT NULL REFERENCES bjorm_it_orders(id), note text, version integer NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE bjorm_it_auto_uuid (id uuid PRIMARY KEY, label text)");
            st.execute("CREATE TABLE bjorm_it_inherited (id uuid PRIMARY KEY, created_at timestamp, name text)");
            st.execute("CREATE TABLE bjorm_it_typed_json (id uuid PRIMARY KEY, values_json jsonb)");
            st.execute("CREATE TABLE bjorm_it_auto_string (id text PRIMARY KEY, label text)");
            st.execute("CREATE TABLE bjorm_it_auto_long (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, label text)");
            st.execute("CREATE TABLE bjorm_it_explicit_assigned (id bigint PRIMARY KEY, label text)");
            st.execute("CREATE TABLE bjorm_it_explicit_identity (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, label text)");
            st.execute("CREATE TABLE bjorm_it_explicit_uuid (id uuid PRIMARY KEY, label text)");
            st.execute("CREATE TABLE bjorm_it_auto_int (id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY, label text)");
            st.execute("CREATE TABLE bjorm_it_record_parents (id uuid PRIMARY KEY, label text)");
            st.execute("CREATE TABLE bjorm_it_record_children (id uuid PRIMARY KEY, parentId uuid NOT NULL REFERENCES bjorm_it_record_parents(id), label text)");
        }
        Bjorm db=Bjorm.open(ds,ItUser_BjormMapper.INSTANCE,ItProduct_BjormMapper.INSTANCE,ItDocument_BjormMapper.INSTANCE,ItOrder_BjormMapper.INSTANCE,ItOrderInner_BjormMapper.INSTANCE,ItOrderOuter_BjormMapper.INSTANCE,ItOrderWithCustomer_BjormMapper.INSTANCE,ItOrderLine_BjormMapper.INSTANCE,ItLineWithUser_BjormMapper.INSTANCE,ItOrderWithNestedReference_BjormMapper.INSTANCE,ItOrderNote_BjormMapper.INSTANCE,AutoUuidRecord_BjormMapper.INSTANCE,AutoStringRecord_BjormMapper.INSTANCE,AutoLongRecord_BjormMapper.INSTANCE,AutoIntRecord_BjormMapper.INSTANCE,AutoUuidPojo_BjormMapper.INSTANCE,AutoParentRecord_BjormMapper.INSTANCE,AutoChildRecord_BjormMapper.INSTANCE,InheritedPatient_BjormMapper.INSTANCE,ItTypedJson_BjormMapper.INSTANCE,ItInstant_BjormMapper.INSTANCE,
            MappedColumns_BjormMapper.INSTANCE,OrdinalEvent_BjormMapper.INSTANCE,
            ItAuditRecord_BjormMapper.INSTANCE,ItAuditPojo_BjormMapper.INSTANCE,
            ExplicitAssignedRecord_BjormMapper.INSTANCE,ExplicitIdentityRecord_BjormMapper.INSTANCE,
            ExplicitUuidRecord_BjormMapper.INSTANCE,StaticSchemaRecord_BjormMapper.INSTANCE,
            ItInvoice_BjormMapper.INSTANCE,ItInvoiceInner_BjormMapper.INSTANCE,
            ItInvoicePojo_BjormMapper.INSTANCE,
            ItEmbeddedRecord_BjormMapper.INSTANCE,ItEmbeddedPojo_BjormMapper.INSTANCE,
            ItCompositeRecord_BjormMapper.INSTANCE,ItCompositePojo_BjormMapper.INSTANCE,ItConvertedRecord_BjormMapper.INSTANCE);
        UUID id=UUID.randomUUID();ItUser alice=new ItUser(id,"BJORM",34);
        try {
            StaticSchemaRecord fixed=db.insertReturning(new StaticSchemaRecord(null,"initial-static"));
            check(db.find(StaticSchemaRecord.class,fixed.id()).label().equals("initial-static"),
                "PostgreSQL fixed-schema INSERT/SELECT");
            db.upsertReturning(new StaticSchemaRecord(fixed.id(),"updated-static"));
            check(db.find(StaticSchemaRecord.class,fixed.id()).label().equals("updated-static"),
                "PostgreSQL fixed-schema UPSERT");
            ItUser invoiceCustomer=db.insertReturning(new ItUser(null,"Invoice Customer",19));
            ItInvoice createdInvoice=db.insertReturning(new ItInvoice(null,invoiceCustomer.id(),"invoice-linked",null));
            java.util.concurrent.atomic.AtomicInteger selects=new java.util.concurrent.atomic.AtomicInteger();
            Bjorm instrumented=db.observeSql((operation,duration,ok)->{
                if(operation==SqlOperation.QUERY)selects.incrementAndGet();
            });
            ItInvoice hydrated=instrumented.find(ItInvoice.class,createdInvoice.id());
            check(hydrated.customer()!=null && hydrated.customer().id().equals(invoiceCustomer.id()) &&
                hydrated.customer().name().equals("Invoice Customer"),
                "PostgreSQL ManyToOne record hydrates parent");
            check(selects.get()==1,"ManyToOne record hydrates in one SQL SELECT, not N+1");
            check(db.select(ItInvoice.class).where(ItInvoice_.customerId.eq(invoiceCustomer.id()))
                    .orderBy(ItInvoice_.reference.asc()).limit(1).fetch().getFirst().customer()!=null,
                "PostgreSQL pageable ManyToOne root query");
            ItInvoice nullParent=db.insertReturning(new ItInvoice(null,null,"invoice-null",null));
            check(db.find(ItInvoice.class,nullParent.id()).customer()==null,
                "LEFT ManyToOne preserves row with NULL FK");
            check(db.find(ItInvoiceInner.class,nullParent.id())==null,
                "INNER ManyToOne excludes row with NULL FK");
            check(db.find(ItInvoiceInner.class,createdInvoice.id()).customer()!=null,
                "INNER ManyToOne hydrates linked record");
            ItInvoicePojo invoicePojo=new ItInvoicePojo();
            invoicePojo.setReference("pojo-linked");
            invoicePojo.setCustomerId(invoiceCustomer.id());
            db.insert(invoicePojo);
            check(db.find(ItInvoicePojo.class,invoicePojo.getId()).getCustomer().id().equals(invoiceCustomer.id()),
                "PostgreSQL ManyToOne POJO setter hydration");
            ItAddress embeddedAddress=new ItAddress("Joinville","89201");
            ItEmbeddedRecord embedded=db.insertReturning(new ItEmbeddedRecord(null,"flat",embeddedAddress));
            check(embeddedAddress.equals(db.find(ItEmbeddedRecord.class,embedded.id()).address()),
                "PostgreSQL embedded record value round-trip");
            check(db.updateBy(ItEmbeddedRecord.class,Map.of("address.street","New Street"),
                    ItEmbeddedRecord_.id.eq(embedded.id()))==1 &&
                db.find(ItEmbeddedRecord.class,embedded.id()).address().street().equals("New Street"),
                "PostgreSQL embedded nested path is safely mapped in updateBy");
            ItEmbeddedPojo embeddedPojo=new ItEmbeddedPojo();
            embeddedPojo.setLabel("pojo-embedded");
            embeddedPojo.setBilling(embeddedAddress);
            db.insert(embeddedPojo);
            check(db.find(ItEmbeddedPojo.class,embeddedPojo.getId()).getBilling().equals(embeddedAddress),
                "PostgreSQL embedded value via POJO getter/setter");
            ItEmbeddedPojo detachedEmbeddedSource=new ItEmbeddedPojo();
            detachedEmbeddedSource.setLabel("pg-detached-embedded");
            detachedEmbeddedSource.setBilling(embeddedAddress);
            ItEmbeddedPojo detachedEmbeddedSaved=db.insertDetached(detachedEmbeddedSource);
            check(detachedEmbeddedSaved!=detachedEmbeddedSource &&
                detachedEmbeddedSaved.getBilling()!=embeddedAddress &&
                detachedEmbeddedSaved.getBilling().equals(embeddedAddress) &&
                detachedEmbeddedSource.getId()==null &&
                detachedEmbeddedSource.getBilling()==embeddedAddress &&
                detachedEmbeddedSaved.getId()!=null,
                "PostgreSQL detached write creates a separate embedded value snapshot");
            try {
                db.insertReturning(new ItEmbeddedRecord(null,"missing",null));
                throw new AssertionError("null @Embedded value accepted");
            } catch(IllegalArgumentException expected) {}
            // Rejection must happen before SQL and must not write a partial row.
            UUID rejectedConvertedId=UUID.randomUUID();
            try {
                db.insertDetached(new ItConvertedRecord(rejectedConvertedId,
                    new MoneyValue(new BigDecimal("19.00")),"unsafe detached"));
                throw new AssertionError("PostgreSQL detached record with converter was accepted");
            } catch(UnsupportedOperationException expected) {}
            check(db.find(ItConvertedRecord.class,rejectedConvertedId)==null,
                "PostgreSQL fail-closed detached copy must not execute INSERT");
            ItConvertedRecord converted=db.insertReturning(new ItConvertedRecord(
                UUID.randomUUID(),new MoneyValue(new BigDecimal("22.50")),"convertible"));
            check(db.find(ItConvertedRecord.class,converted.id()).amount().equals(converted.amount()),
                "PostgreSQL generated JDBC converter round-trip");
            check(db.list(ItConvertedRecord.class,
                ItConvertedRecord_.amount.eq(new MoneyValue(new BigDecimal("22.50")))).size()==1,
                "PostgreSQL typed equality converts domain values to JDBC parameters");
            check(db.list(ItConvertedRecord.class,
                ItConvertedRecord_.amount.in(List.of(new MoneyValue(new BigDecimal("22.50"))))).size()==1,
                "PostgreSQL typed IN converts domain values");
            check(db.updateBy(ItConvertedRecord.class,
                Map.of("amount",new MoneyValue(new BigDecimal("31.75"))),
                ItConvertedRecord_.id.eq(converted.id()))==1 &&
                db.find(ItConvertedRecord.class,converted.id()).amount().amount().compareTo(new BigDecimal("31.75"))==0,
                "PostgreSQL generated converter supports bulk mapped update");
            UUID compositeTenant=UUID.randomUUID();
            CompositeId compoundId=CompositeId.of(compositeTenant,"ABC");
            ItCompositeRecord createdComposite=db.insertReturning(new ItCompositeRecord(compositeTenant,"ABC","first",0));
            check(db.find(ItCompositeRecord.class,compoundId).equals(createdComposite),
                "PostgreSQL tuple-key find binds every @Id");
            ItCompositeRecord revisedComposite=db.updateReturning(new ItCompositeRecord(compositeTenant,"ABC","second",0));
            check(revisedComposite.version()==1 && revisedComposite.label().equals("second"),
                "PostgreSQL composite key optimistic update and reload");
            try {db.update(new ItCompositeRecord(compositeTenant,"ABC","stale",0));
                throw new AssertionError("stale composite key update accepted");}
            catch(OptimisticLockException expected) {}
            try {db.updateBy(ItCompositeRecord.class,Map.of("sku","changed"),
                ItCompositeRecord_.sku.eq("ABC"));
                throw new AssertionError("composite key part changed via updateBy");}
            catch(IllegalArgumentException expected) {}
            check(db.delete(revisedComposite)==1 &&
                db.find(ItCompositeRecord.class,compoundId)==null,
                "PostgreSQL tuple key conditional DELETE");
            ItCompositePojo tuplePojo=new ItCompositePojo();
            tuplePojo.setTenantId(compositeTenant);tuplePojo.setSku("POJO");tuplePojo.setLabel("tuple");
            db.insert(tuplePojo);
            check(db.find(ItCompositePojo.class,CompositeId.of(compositeTenant,"POJO")).getLabel().equals("tuple"),
                "PostgreSQL composite key POJO roundtrip");
            ExplicitAssignedRecord assigned=db.insertReturning(new ExplicitAssignedRecord(709L,"assigned"));
            check(assigned.id()==709L && db.find(ExplicitAssignedRecord.class,709L).equals(assigned),
                "PostgreSQL explicit assigned numeric ID");
            ExplicitIdentityRecord identity=db.insertReturning(new ExplicitIdentityRecord(null,"identity"));
            check(identity.id()!=null && identity.id()>0 &&
                db.find(ExplicitIdentityRecord.class,identity.id()).equals(identity),
                "PostgreSQL explicit database identity");
            ExplicitUuidRecord uuidRecord=db.insertReturning(new ExplicitUuidRecord(null,"v7"));
            check(uuidRecord.id()!=null && uuidRecord.id().version()==7 &&
                db.find(ExplicitUuidRecord.class,uuidRecord.id()).equals(uuidRecord),
                "PostgreSQL explicit UUID v7");
            Bjorm required=Bjorm.tenantRequired(ds,BjormOptions.defaults(),SqlDialects.POSTGRESQL,ItUser_BjormMapper.INSTANCE);
            try {required.find(ItUser.class,UUID.randomUUID());throw new AssertionError("unscoped access accepted");}
            catch(IllegalStateException expected) {}
            Bjorm tenantA=required.forTenant("bjorm_tenant_a"),tenantB=required.forTenant("bjorm_tenant_b");
            UUID sharedId=UUID.randomUUID();
            tenantA.insert(new ItUser(sharedId,"Alice A",11));
            tenantB.insert(new ItUser(sharedId,"Alice B",22));
            check(tenantA.find(ItUser.class,sharedId).name().equals("Alice A"),"tenant A isolation");
            long scopedCount=tenantA.withStream(tenantA.select(ItUser.class).limit(5),rows->rows.count());
            check(scopedCount==1,"PostgreSQL transaction-bound streamed tenant SELECT");
            try {
                tenantA.execute("UPDATE bjorm_tenant_b.bjorm_it_users SET age=777 WHERE id=?",
                    tenantA.params(sharedId));
                throw new AssertionError("tenant A crossed schema via native SQL");
            } catch(SecurityException expected) {}
            check(tenantB.find(ItUser.class,sharedId).age()==22,
                "qualified native SQL cannot alter a different tenant");
            tenantA.tx(tx -> {
                ItUser first=tx.find(ItUser.class,sharedId);
                check(first==tx.find(ItUser.class,sharedId),"tenant A transaction reuses identity");
                check("Alice A".equals(first.name()),"tenant A cached identity is isolated");
            });
            tenantB.tx(tx -> {
                ItUser first=tx.find(ItUser.class,sharedId);
                check(first==tx.find(ItUser.class,sharedId),"tenant B transaction reuses identity");
                check("Alice B".equals(first.name()),"tenant B cannot see tenant A cached entity");
            });
            check(tenantB.find(ItUser.class,sharedId).name().equals("Alice B"),"tenant B isolation");
            check(db.find(ItUser.class,sharedId)==null,"PostgreSQL tenant scope never leaks into public");
            tenantA.tx(tx->tx.updateBy(ItUser.class,Map.of("age",33),ItUser_.id.eq(sharedId)));
            check(tenantA.find(ItUser.class,sharedId).age()==33 &&
                tenantB.find(ItUser.class,sharedId).age()==22,"tenant-local transaction mutations");
            check(tenantA.deleteBy(ItUser.class,ItUser_.id.eq(sharedId))==1,"tenant-local bulk delete");
            check(tenantA.find(ItUser.class,sharedId)==null &&
                tenantB.find(ItUser.class,sharedId)!=null,"deletion cannot cross tenants");
            ItAuditRecord generated=db.insertReturning(new ItAuditRecord(null,"created",null,null));
            ItAuditRecord initialAudit=db.find(ItAuditRecord.class,generated.id());
            check(initialAudit.createdAt()!=null && initialAudit.updatedAt()!=null,
                "PostgreSQL generated audit timestamps");
            ItAuditRecord changedAudit=db.updateReturning(new ItAuditRecord(initialAudit.id(),
                "updated",initialAudit.createdAt(),initialAudit.updatedAt()));
            check(changedAudit.createdAt().equals(initialAudit.createdAt()) &&
                !changedAudit.updatedAt().isBefore(initialAudit.updatedAt()),
                "PostgreSQL audit updates retain original creation date");
            check(db.updateBy(ItAuditRecord.class,Map.of("label","updated bulk"),
                ItAuditRecord_.id.eq(generated.id()))==1,"PostgreSQL bulk @UpdatedAt stamp");
            ItAuditRecord afterBulk=db.find(ItAuditRecord.class,generated.id());
            check(!afterBulk.updatedAt().isBefore(changedAudit.updatedAt()),
                "PostgreSQL bulk audit auto-update");
            try {db.updateBy(ItAuditRecord.class,Map.of("createdAt",Instant.now()),
                ItAuditRecord_.id.eq(generated.id()));
                throw new AssertionError("Creation timestamp bulk mutation accepted");
            } catch(IllegalArgumentException expected) {}
            ItAuditRecord upsertedAudit=db.upsertReturning(new ItAuditRecord(generated.id(),
                "upserted",Instant.parse("2000-01-01T00:00:00Z"),afterBulk.updatedAt()));
            ItAuditRecord persistedAudit=db.find(ItAuditRecord.class,generated.id());
            check(upsertedAudit.createdAt().equals(initialAudit.createdAt()) &&
                persistedAudit.createdAt().equals(initialAudit.createdAt()),
                "PostgreSQL UPSERT must preserve creation time");
            ItAuditPojo auditPojo=new ItAuditPojo();auditPojo.setLabel("pojo");db.insert(auditPojo);
            check(auditPojo.getCreatedAt()!=null && auditPojo.getUpdatedAt()!=null &&
                db.find(ItAuditPojo.class,auditPojo.getId()).getCreatedAt()!=null,
                "PostgreSQL POJO audit timestamps");
            Instant pojoAuditBefore=auditPojo.getUpdatedAt();
            auditPojo.setLabel("rolled-back audit update");
            try {
                db.tx(tx->{
                    tx.update(auditPojo);
                    throw new IllegalStateException("Rollback audit update");
                });
                throw new AssertionError("Expected audit rollback");
            } catch(IllegalStateException expected) {}
            check(auditPojo.getUpdatedAt().equals(pojoAuditBefore),
                "PostgreSQL rollback must not mutate caller POJO @UpdatedAt");
            check(db.find(ItAuditPojo.class,auditPojo.getId()).getLabel().equals("pojo"),
                "PostgreSQL rollback restored stored audit POJO");
            ItAuditPojo committedPojo=db.updateReturning(auditPojo);
            check(committedPojo.getLabel().equals("rolled-back audit update") &&
                !committedPojo.getUpdatedAt().isBefore(pojoAuditBefore) &&
                auditPojo.getUpdatedAt().equals(pojoAuditBefore),
                "PostgreSQL updateReturning refreshes timestamp without touching caller");
            ItAuditPojo detachedSource=new ItAuditPojo();
            detachedSource.setLabel("detached insert");
            ItAuditPojo detachedPersisted=db.insertDetached(detachedSource);
            check(detachedPersisted!=detachedSource && detachedPersisted.getId()!=null &&
                detachedPersisted.getCreatedAt()!=null && detachedPersisted.getUpdatedAt()!=null &&
                detachedSource.getId()==null && detachedSource.getCreatedAt()==null &&
                detachedSource.getUpdatedAt()==null,
                "PostgreSQL detached INSERT keeps caller unchanged");
            ItAuditPojo duplicateDetached=new ItAuditPojo();
            duplicateDetached.setId(detachedPersisted.getId());
            duplicateDetached.setLabel("duplicate");
            try {
                db.insertDetached(duplicateDetached);
                throw new AssertionError("Expected PostgreSQL duplicate detached INSERT to fail");
            } catch(BjormException expected) {}
            check(duplicateDetached.getCreatedAt()==null && duplicateDetached.getUpdatedAt()==null &&
                db.find(ItAuditPojo.class,detachedPersisted.getId()).getLabel().equals("detached insert"),
                "PostgreSQL detached INSERT failure cannot stamp caller or change stored row");
            ItAuditPojo detachedUpsertSource=new ItAuditPojo();
            detachedUpsertSource.setId(detachedPersisted.getId());
            detachedUpsertSource.setLabel("detached upsert");
            ItAuditPojo detachedUpsertPersisted=db.upsertDetached(detachedUpsertSource);
            check(detachedUpsertPersisted!=detachedUpsertSource &&
                detachedUpsertSource.getUpdatedAt()==null && detachedUpsertSource.getCreatedAt()==null &&
                db.find(ItAuditPojo.class,detachedPersisted.getId()).getLabel().equals("detached upsert"),
                "PostgreSQL detached UPSERT copies timestamps without touching caller");
            ItAuditPojo rolledBackSource=new ItAuditPojo();
            rolledBackSource.setLabel("detached rollback");
            java.util.concurrent.atomic.AtomicReference<UUID> rolledBackId=new java.util.concurrent.atomic.AtomicReference<>();
            try {
                db.tx(tx->{
                    rolledBackId.set(tx.insertDetached(rolledBackSource).getId());
                    throw new IllegalStateException("detached rollback");
                });
                throw new AssertionError("Expected detached rollback");
            } catch(IllegalStateException expected) {}
            check(rolledBackSource.getId()==null && rolledBackSource.getCreatedAt()==null &&
                rolledBackSource.getUpdatedAt()==null && rolledBackId.get()!=null &&
                db.find(ItAuditPojo.class,rolledBackId.get())==null,
                "PostgreSQL external transaction rollback does not mutate detached caller");
            Instant time=Instant.parse("2026-11-10T10:20:30Z");
            ItInstant savedInstant=db.insertReturning(new ItInstant(null,"Original",time,0));
            check(db.find(ItInstant.class,savedInstant.id()).occurredAt().equals(time),
                "PostgreSQL generated INSERT/SELECT Instant");
            check(db.update(new ItInstant(savedInstant.id(),"Edited",time.plusSeconds(60),0))==1,
                "PostgreSQL generated UPDATE Instant");
            check(db.find(ItInstant.class,savedInstant.id()).occurredAt().equals(time.plusSeconds(60)),
                "PostgreSQL generated Instant update roundtrip");
            ItInstant refreshed=db.updateReturning(new ItInstant(savedInstant.id(),"Updated again",time,1));
            check(refreshed.version()==2,"updateReturning refreshes database version");
            ItInstant upserted=db.upsertReturning(new ItInstant(savedInstant.id(),"Upserted",time,2));
            check(upserted.version()==3,"versioned UPSERT returns committed increment");
            try {db.updateReturning(new ItInstant(savedInstant.id(),"stale",time,1));
                throw new AssertionError("stale update should fail");
            } catch(OptimisticLockException expected) {}
            ItInstant absentTime=db.insertReturning(new ItInstant(null,"Null",null,0));
            check(db.find(ItInstant.class,absentTime.id()).occurredAt()==null,
                "PostgreSQL generated nullable Instant roundtrip");
            InheritedPatient inherited=new InheritedPatient();
            inherited.setName("Inherited");
            inherited.setCreatedAt(java.time.LocalDateTime.of(2026,10,8,10,0));
            db.insert(inherited);
            InheritedPatient reloaded=db.find(InheritedPatient.class,inherited.getId());
            check(reloaded!=null && reloaded.getId().equals(inherited.getId()) &&
                  reloaded.getCreatedAt().equals(inherited.getCreatedAt()) && reloaded.getName().equals("Inherited"),
                  "real PostgreSQL inherited fields");
            ItTypedJson jsonEntity=db.insertReturning(new ItTypedJson(null,List.of(2,5,8)));
            check(jsonEntity.id().version()==7 && db.find(ItTypedJson.class,jsonEntity.id()).values().equals(List.of(2,5,8)),
                  "real PostgreSQL typed JSON list round-trip");
            db.insertReturning(new ItTypedJson(null,null));
            check(db.list(ItTypedJson.class).stream().anyMatch(x->x.values()==null),
                  "typed JSON null round-trip");
            AutoUuidPojo pojo=new AutoUuidPojo();pojo.setLabel("pojo");db.insert(pojo);
            check(pojo.getId()!=null && pojo.getId().version()==7,"POJO auto UUID");
            AutoUuidRecord ar=new AutoUuidRecord(null,"record");
            ar=db.insertReturning(ar);
            check(ar.id()!=null && ar.id().version()==7 && db.find(AutoUuidRecord.class,ar.id()).equals(ar),"record auto UUID");
            check(db.insertReturning(new AutoStringRecord(null,"string")).id().length()==36,"String ID UUID v7");
            AutoLongRecord longId=db.insertReturning(new AutoLongRecord(null,"num"));
            check(longId.id()!=null && longId.id()>0,"Long generated by PostgreSQL");
            check(db.insertReturning(new AutoIntRecord(0,"num")).id()>0,"int generated by PostgreSQL");
            var many=db.batchInsertReturning(List.of(new AutoUuidRecord(null,"batch-1"),new AutoUuidRecord(null,"batch-2")));
            check(many.size()==2 && many.getFirst().id()!=null && !many.getFirst().id().equals(many.getLast().id()),"record UUID batch");
            AutoParentRecord root=db.insertReturning(new AutoParentRecord(null,"root",List.of(new AutoChildRecord(null,null,"child"))));
            check(root.id()!=null && root.children().getFirst().parentId().equals(root.id()) && root.children().getFirst().id()!=null,"immutable graph IDs/FK");
            check(db.list(AutoChildRecord.class,AutoChildRecord_.parentId.eq(root.id())).size()==1,"immutable children persisted");
            check(db.find(AutoParentRecord.class,root.id()).children().size()==1,
                  "record graph JOIN returns reconstructed immutable child list");
            check(db.delete(root)==1,"record graph delete");
            check(db.list(AutoChildRecord.class,AutoChildRecord_.parentId.eq(root.id())).isEmpty(),"record graph cascade delete");
            db.insert(alice);
            check(db.find(ItUser.class,id).equals(alice),"actual database CRUD");
            check(db.list(ItUser.class,ItUser_.name.eq("BJORM")).stream().anyMatch(x->x.id().equals(id)),"DSL binding against PostgreSQL");
            check(db.findOne(ItUser.class,ItUser_.name.eq("BJORM")).orElseThrow().equals(alice),"findOne Optional against PostgreSQL");
            check(db.findOne(ItUser.class,ItUser_.name.eq("missing")).isEmpty(),"findOne missing record");
            check(db.count(ItUser.class,ItUser_.name.eq("BJORM")) == 1L, "mapped count predicate");
            check(db.count(ItUser.class,null) >= 1L, "mapped total count");
            MappedColumns mapped=db.insertReturning(new MappedColumns(null,"immutable","ignored",77,"never-persist"));
            MappedColumns columns=db.find(MappedColumns.class,mapped.id());
            check("immutable".equals(columns.readOnly()) && "server-default".equals(columns.serverValue()) &&
                columns.localCounter()==0 && columns.localHint()==null,
                "PostgreSQL transient and generated column metadata roundtrip");
            try {db.updateBy(MappedColumns.class,Map.of("readOnly","mutated"),MappedColumns_.id.eq(mapped.id()));
                throw new AssertionError("read-only column updated");}
            catch(IllegalArgumentException expected) {}
            OrdinalEvent ordinal=db.insertReturning(new OrdinalEvent(null,Status.INACTIVE,"enum ordinal"));
            check(db.find(OrdinalEvent.class,ordinal.id()).status()==Status.INACTIVE,"PostgreSQL numeric enum roundtrip");
            UUID bulkId=UUID.randomUUID();
            db.batchUpsertReturning(List.of(new ItUser(bulkId,"NativeBatch",10)));
            check(db.find(ItUser.class,bulkId).age()==10,"batchUpsertReturning inserts");
            check(db.updateBy(ItUser.class,Map.of("age",99),ItUser_.id.eq(bulkId))==1,
                "mapped updateBy parameters");
            check(db.find(ItUser.class,bulkId).age()==99,"mapped updateBy values persisted");
            db.batchDelete(List.of(new ItUser(bulkId,"NativeBatch",99)));
            check(db.find(ItUser.class,bulkId)==null,"batchDelete removes entity");
            UUID secondaryId=UUID.randomUUID();
            db.insert(new ItUser(secondaryId,"deleteBy",1));
            check(db.deleteBy(ItUser.class,ItUser_.id.eq(secondaryId))==1 &&
                db.find(ItUser.class,secondaryId)==null,"predicate deleteBy");
            try {db.deleteBy(ItProduct.class,ItProduct_.name.eq("anything"));
                throw new AssertionError("versioned bulk delete accepted");}
            catch(IllegalArgumentException expected) {}
            UUID secondId=UUID.randomUUID();
            db.insert(new ItUser(secondId,"Cursor User",20));
            UUID lower=db.select(ItUser.class).orderBy(db.primaryKeyOrder(ItUser.class,false))
                .limit(1).fetch().getFirst().id();
            var sought=db.select(ItUser.class).where(db.seekAfterId(ItUser.class,lower,false))
                .orderBy(db.primaryKeyOrder(ItUser.class,false)).limit(2).fetch();
            check(!sought.isEmpty() && sought.size()<=2 &&
                  sought.stream().noneMatch(candidate->candidate.id().equals(lower)),
                "real keyset seek skips cursor ID even with additional users");
            check(db.select(ItUser.class).orderBy(db.mappedOrder(ItUser.class,"name",false)).limit(1).offset(0L)
                .fetch().size()==1, "mapped order with long offset");
            try {db.mappedOrder(ItUser.class,"notMapped",false);throw new AssertionError("unsafe sort property accepted");}
            catch(IllegalArgumentException expected) {}
            var fields=db.findFields(ItUser.class,ItUser_.name.eq("BJORM"),"name","age");
            check(fields.size()==1 && fields.getFirst().get("age").equals(34),"named Java properties return only selected columns");
            check(db.findColumnOne(ItUser.class,"name",String.class,ItUser_.age.eq(34)).orElseThrow().equals("BJORM"),"typed scalar projection");
            Instant testInstant=Instant.parse("2026-11-10T10:20:30Z");
            Instant returned=db.one("SELECT CAST(? AS TIMESTAMPTZ)",
                db.params(testInstant),
                rs->rs.getTimestamp(1).toInstant());
            check(testInstant.equals(returned),"native BJORM Instant binder PostgreSQL TIMESTAMPTZ");
            check(db.count(ItUser.class, ItUser_.name.eq("BJORM")) == 1L, "real PostgreSQL aggregate COUNT");
            check(db.exists(ItUser.class, ItUser_.name.eq("BJORM")), "real PostgreSQL EXISTS with predicate");
            check(!db.exists(ItUser.class, ItUser_.name.eq("MISSING")), "real PostgreSQL EXISTS false");
            db.tx(tx -> {
                check(tx.count(ItUser.class, ItUser_.name.eq("BJORM")) == 1L, "transaction COUNT");
                check(tx.exists(ItUser.class, ItUser_.name.eq("BJORM")), "transaction EXISTS");
                check(tx.lockOne(ItUser.class, ItUser_.name.eq("BJORM"), LockMode.UPDATE),
                    "PostgreSQL FOR UPDATE in transaction");
                check(tx.lockOne(ItUser.class, ItUser_.name.eq("BJORM"), LockMode.SHARE),
                    "PostgreSQL FOR SHARE in transaction");
                check(!tx.lockOne(ItUser.class, ItUser_.name.eq("MISSING"), LockMode.UPDATE),
                    "PostgreSQL lock absent row");
            });

            String explicitIsolation=db.txResult(TransactionIsolation.SERIALIZABLE,
                tx->tx.one("SHOW transaction_isolation",rs->rs.getString(1)));
            check("serializable".equalsIgnoreCase(explicitIsolation),
                "PostgreSQL honors explicit SERIALIZABLE isolation within BJORM transaction");
            db.tx(TransactionIsolation.READ_COMMITTED,tx->
                check(tx.count(ItUser.class,ItUser_.name.eq("BJORM"))==1L,
                    "PostgreSQL READ COMMITTED transaction accepts mapped SQL"));
            try{db.tx(tx->{tx.insert(new ItUser(UUID.randomUUID(),"SHOULD_ROLLBACK",11));throw new IllegalStateException("rollback");});}catch(IllegalStateException expected){}
            check(db.list(ItUser.class,ItUser_.name.eq("SHOULD_ROLLBACK")).isEmpty(),"real transaction rollback");
            ItProduct product=new ItProduct(id,"Pencil",new BigDecimal("4.50"),Status.ACTIVE,0);
            db.insert(product);
            var selectedPrice=db.findOneFields(ItProduct.class,ItProduct_.name.eq("Pencil"),"price").orElseThrow();
            check(selectedPrice.get("price").equals(new BigDecimal("4.50")),"renamed @Column projects as Java property");
            check(db.select(ItProduct.class).fields("id","name").fetch().getFirst().get("name").equals("Pencil"),"DSL property selection");
            check(new ItQueries_Bjorm(db).productsByUserName("BJORM").size()==1,"compiled join against real database");
            db.insert(new ItDocument(UUID.randomUUID(),"{\"valid\":true}"));
            check(db.list(ItDocument.class).getFirst().payload().contains("valid"),"PostgreSQL jsonb serialization");
            check(db.update(product)==1,"version update");
            try{db.update(product);throw new AssertionError("stale update not detected");}catch(OptimisticLockException expected){}
            db.tx(tx->tx.batchInsert(List.of(new ItUser(UUID.randomUUID(),"Batch1",1),new ItUser(UUID.randomUUID(),"Batch2",2))));
            check(db.list(ItUser.class,ItUser_.name.in(List.of("Batch1","Batch2"))).size()==2,"batch committed");
            ItOrderWithCustomer mixedOrder=new ItOrderWithCustomer();
            mixedOrder.setCustomerId(invoiceCustomer.id());
            mixedOrder.setDescription("Combined order");
            ItOrderLine mixedLine=new ItOrderLine();
            mixedLine.setSku("COMBINED");
            mixedOrder.getLines().add(mixedLine);
            db.insert(mixedOrder);
            java.util.concurrent.atomic.AtomicInteger mixedQueries=new java.util.concurrent.atomic.AtomicInteger();
            Bjorm observedGraph=db.observeSql((operation,nanos,ok)->{
                if(operation==SqlOperation.QUERY)mixedQueries.incrementAndGet();
            });
            ItOrderWithCustomer mixedLoaded=observedGraph.find(ItOrderWithCustomer.class,mixedOrder.getId());
            check(mixedLoaded.getCustomer()!=null &&
                mixedLoaded.getCustomer().id().equals(invoiceCustomer.id()) &&
                mixedLoaded.getLines().size()==1 &&
                mixedLoaded.getLines().getFirst().getSku().equals("COMBINED"),
                "PostgreSQL graph hydrates @ManyToOne and @Children together");
            check(mixedQueries.get()==1,
                "combined PostgreSQL eager graph must use one SQL, not N+1");
            check(db.delete(mixedOrder)==1 && db.find(ItOrderWithCustomer.class,mixedOrder.getId())==null,
                "combined graph cascade does not delete referenced customer");
            check(db.find(ItUser.class,invoiceCustomer.id())!=null,
                "referenced customer is not cascaded by graph delete");
            ItOrder order = new ItOrder();
            order.setDescription("First order");
            ItOrderLine line = new ItOrderLine();
            line.setSku("SKU-1");
            order.getLines().add(line);
            ItOrderNote note=new ItOrderNote();note.setNote("Note 1");
            order.getNotes().add(note);
            db.insert(order);
            check(order.getId() != null && order.getId().version() == 7 && order.getId().variant() == 2, "native UUID v7 parent");
            check(line.getId() != null && line.getId().version() == 7, "native UUID v7 child");
            check(order.getId().equals(line.getOrderId()), "child FK assigned after parent ID generation");
            check(db.list(ItOrderLine.class, ItOrderLine_.orderId.eq(order.getId())).size() == 1, "child saved within parent transaction");
            ItOrder fromJoin=db.find(ItOrder.class,order.getId());
            check(fromJoin.getLines().size()==1 && fromJoin.getNotes().size()==1, "single SQL graph JOIN loads siblings");
            check(db.find(ItOrderInner.class,order.getId()).getNotes().size()==1,
                "@Children INNER JOIN on PostgreSQL");
            check(db.find(ItOrderOuter.class,order.getId()).getLines().size()==1,
                "@Children RIGHT/FULL JOIN on PostgreSQL");
            var explicit=db.select(ItOrder.class).as("o")
                .join(ItOrderLine.class,"l").on(ItOrder_.id.as("o").sameAs(ItOrderLine_.orderId.as("l")))
                .fetch();
            check(explicit.size()==1 && explicit.getFirst().getNotes().size()==1,
                "explicit JOIN with eager children in one SQL");
            check(db.select(ItUser.class).as("u").fullJoin(ItProduct.class,"p")
                .on(ItUser_.id.as("u").sameAs(ItProduct_.id.as("p"))).fetch().size()>=1,
                "real PostgreSQL FULL OUTER JOIN");
            check(fromJoin.getLines().getFirst().getId().equals(line.getId()) &&
                  fromJoin.getNotes().getFirst().getId().equals(note.getId()), "joined children mapped without duplicates");
            check(db.select(ItOrder.class).orderBy(ItOrder_.description.asc()).limit(1).fetch().getFirst().getNotes().size()==1,
                  "paginated graph joins children AFTER paging root");
            check(db.list(ItOrder.class,ItOrder_.description.eq("First order")).getFirst().getNotes().size()==1,
                  "list loads child graphs eagerly");
            check(db.findOne(ItOrder.class,ItOrder_.description.eq("First order")).orElseThrow().getLines().size()==1,
                  "first loads child graphs eagerly");
            order.setDescription("Updated order");
            line.setSku("SKU-2");
            check(db.upsert(order) == 1, "native PostgreSQL upsert");
            check(db.find(ItOrder.class, order.getId()).getDescription().equals("Updated order"), "upsert updates parent");
            check(db.find(ItOrderLine.class, line.getId()).getSku().equals("SKU-2"), "upsert updates child");
            // The loaded parent has no in-memory child collection. Deletion discovers children in the DB.
            check(db.delete(db.find(ItOrder.class, order.getId())) == 1, "delete parent graph");
            check(db.list(ItOrderLine.class, ItOrderLine_.orderId.eq(order.getId())).isEmpty(), "cascade deletion from DB, not in-memory list");
            check(db.list(ItOrderNote.class,ItOrderNote_.orderId.eq(order.getId())).isEmpty(), "versioned child cascade deletion");
            ItOrder bad = new ItOrder();
            bad.setDescription("Must roll back");
            ItOrderLine conflict = new ItOrderLine();
            conflict.setSku("bad-fk");
            conflict.setOrderId(UUID.randomUUID());
            bad.getLines().add(conflict);
            try { db.insert(bad); throw new AssertionError("Expected foreign key rejection"); }
            catch(IllegalArgumentException expected) { }
            check(db.list(ItOrder.class, ItOrder_.description.eq("Must roll back")).isEmpty(), "cascade insert failure rolled back root");
            ItUser nestedUser=db.insertReturning(new ItUser(null,"Nested child customer PostgreSQL",29));
            ItOrderWithNestedReference nestedOrder=db.insertReturning(
                new ItOrderWithNestedReference(UUID.randomUUID(),"nested to-one PostgreSQL",null,
                    List.of(new ItLineWithUser(null,null,nestedUser.id(),"REF",null),
                            new ItLineWithUser(null,null,null,"OPTIONAL",null))));
            java.util.concurrent.atomic.AtomicInteger nestedQueries=new java.util.concurrent.atomic.AtomicInteger();
            Bjorm observedNested=db.observeSql((operation,nanos,ok)->{
                if(operation==SqlOperation.QUERY)nestedQueries.incrementAndGet();
            });
            ItOrderWithNestedReference nestedLoaded=observedNested
                .select(ItOrderWithNestedReference.class)
                .where(ItOrderWithNestedReference_.id.eq(nestedOrder.id()))
                .limit(1).fetch().getFirst();
            check(nestedLoaded.lines().size()==2 &&
                nestedLoaded.lines().stream().anyMatch(item->
                    "REF".equals(item.sku()) && item.customer()!=null &&
                    item.customer().id().equals(nestedUser.id())) &&
                nestedLoaded.lines().stream().anyMatch(item->
                    "OPTIONAL".equals(item.sku()) && item.customer()==null),
                "PostgreSQL nested child @ManyToOne hydration with nullable FK");
            check(nestedQueries.get()==1,
                "PostgreSQL nested child @ManyToOne graph must use one JDBC query");
            // Two distinct real JDBC connections contend for the same row.
            // SKIP LOCKED must return promptly without observing the locked row;
            // NOWAIT must raise the database's documented lock-not-available error.
            try(Connection owner=ds.getConnection()) {
                owner.setAutoCommit(false);
                try(PreparedStatement blocker=owner.prepareStatement(
                    "SELECT id FROM bjorm_it_users WHERE id = ? FOR UPDATE")) {
                    blocker.setObject(1,invoiceCustomer.id());
                    try(ResultSet held=blocker.executeQuery()) {
                        check(held.next(),"PostgreSQL blocker locked expected user row");
                    }
                }
                for(LockMode mode:List.of(LockMode.UPDATE_SKIP_LOCKED,LockMode.SHARE_SKIP_LOCKED)) {
                    boolean selected=db.txResult(tx->
                        tx.lockOne(ItUser.class,ItUser_.id.eq(invoiceCustomer.id()),mode));
                    check(!selected,"PostgreSQL "+mode+" must skip an exclusively locked row");
                }
                for(LockMode mode:List.of(LockMode.UPDATE_NOWAIT,LockMode.SHARE_NOWAIT)) {
                    try {
                        db.tx(tx->tx.lockOne(ItUser.class,ItUser_.id.eq(invoiceCustomer.id()),mode));
                        throw new AssertionError("PostgreSQL NOWAIT unexpectedly acquired a blocked row");
                    } catch(BjormException refused) {
                        Throwable error=refused.getCause();
                        check(error instanceof SQLException && "55P03".equals(((SQLException)error).getSQLState()),
                            "PostgreSQL NOWAIT must report the vendor lock-not-available code");
                    }
                }
                owner.rollback();
            }
            System.out.println("PASS: PostgreSQL CRUD, DSL, version, rollback, batch, UUID v7, upsert and nested cascade");
        } finally {
            try(Connection c=ds.getConnection();Statement st=c.createStatement()){
                st.execute("DROP TABLE IF EXISTS bjorm_it_record_children");
                st.execute("DROP TABLE IF EXISTS bjorm_it_record_parents");
                st.execute("DROP TABLE IF EXISTS bjorm_it_auto_int");
                st.execute("DROP TABLE IF EXISTS bjorm_it_explicit_assigned");
                st.execute("DROP TABLE IF EXISTS bjorm_it_explicit_identity");
                st.execute("DROP TABLE IF EXISTS bjorm_it_explicit_uuid");
                st.execute("DROP TABLE IF EXISTS bjorm_it_auto_long");
                st.execute("DROP TABLE IF EXISTS bjorm_it_auto_string");
                st.execute("DROP TABLE IF EXISTS bjorm_it_auto_uuid");
                st.execute("DROP TABLE IF EXISTS bjorm_it_order_lines_to_one");
                st.execute("DROP TABLE IF EXISTS bjorm_it_order_lines");
                st.execute("DROP TABLE IF EXISTS bjorm_it_order_notes");
                st.execute("DROP TABLE IF EXISTS bjorm_it_orders");
                st.execute("DROP SCHEMA IF EXISTS bjorm_test CASCADE");
                st.execute("DROP SCHEMA IF EXISTS bjorm_tenant_a CASCADE");
                st.execute("DROP SCHEMA IF EXISTS bjorm_tenant_b CASCADE");
                st.execute("DROP TABLE IF EXISTS bjorm_it_composite");
                st.execute("DROP TABLE IF EXISTS bjorm_it_composite_pojo");
                st.execute("DROP TABLE IF EXISTS bjorm_it_invoices");
                st.execute("DROP TABLE IF EXISTS bjorm_it_embedded_records");
                st.execute("DROP TABLE IF EXISTS bjorm_it_embedded_pojos");
                st.execute("DROP TABLE IF EXISTS bjorm_it_converted");
                st.execute("DROP TABLE IF EXISTS bjorm_it_users");
                st.execute("DROP TABLE IF EXISTS bjorm_it_columns");
                st.execute("DROP TABLE IF EXISTS bjorm_it_ordinal");
                st.execute("DROP TABLE IF EXISTS bjorm_it_audit");
                st.execute("DROP TABLE IF EXISTS bjorm_it_audit_pojo");
                st.execute("DROP TABLE IF EXISTS bjorm_it_products");
                st.execute("DROP TABLE IF EXISTS bjorm_it_instants");
                st.execute("DROP TABLE IF EXISTS bjorm_it_documents");
            }
        }
    }
}
