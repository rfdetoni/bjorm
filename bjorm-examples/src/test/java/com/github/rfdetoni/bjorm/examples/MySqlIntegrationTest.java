package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.*;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** Executed only with disposable MySQL 8.4 DB; intentionally owns its fixture tables. */
public final class MySqlIntegrationTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        String url=System.getenv("BJORM_MYSQL_JDBC_URL");
        if(url==null||url.isBlank()){System.out.println("SKIP: BJORM_MYSQL_JDBC_URL not configured");return;}
        String user=System.getenv().getOrDefault("BJORM_MYSQL_USER","bjorm");
        String pass=System.getenv().getOrDefault("BJORM_MYSQL_PASSWORD","bjorm_test");
        DataSource source=new DataSource(){
            public Connection getConnection()throws SQLException{return DriverManager.getConnection(url,user,pass);}
            public Connection getConnection(String u,String p)throws SQLException{return DriverManager.getConnection(url,u,p);}
            public PrintWriter getLogWriter(){return null;}
            public void setLogWriter(PrintWriter w){}
            public void setLoginTimeout(int v){}
            public int getLoginTimeout(){return 0;}
            public Logger getParentLogger(){return Logger.getGlobal();}
            public <T>T unwrap(Class<T> cls)throws SQLException{throw new SQLException("Unsupported");}
            public boolean isWrapperFor(Class<?> cls){return false;}
        };
        try(Connection c=source.getConnection();Statement st=c.createStatement()){
            st.execute("CREATE TABLE bjorm_it_users(id CHAR(36) PRIMARY KEY,name VARCHAR(255) UNIQUE,age INT NOT NULL)");
            st.execute("CREATE TABLE bjorm_it_invoices(id CHAR(36) PRIMARY KEY,customerId CHAR(36),reference VARCHAR(255),FOREIGN KEY(customerId) REFERENCES bjorm_it_users(id))");
            st.execute("CREATE TABLE bjorm_it_composite(tenantId CHAR(36) NOT NULL,sku VARCHAR(255) NOT NULL,label VARCHAR(255),version INT NOT NULL DEFAULT 0,PRIMARY KEY(tenantId,sku))");
            st.execute("CREATE TABLE bjorm_it_composite_pojo(tenantId CHAR(36) NOT NULL,sku VARCHAR(255) NOT NULL,label VARCHAR(255),PRIMARY KEY(tenantId,sku))");
            st.execute("CREATE TABLE bjorm_it_embedded_records(id CHAR(36) PRIMARY KEY,label VARCHAR(255),shipping_street VARCHAR(255),shipping_postal VARCHAR(255))");
            st.execute("CREATE TABLE bjorm_it_embedded_pojos(id CHAR(36) PRIMARY KEY,label VARCHAR(255),billing_street VARCHAR(255),billing_postal VARCHAR(255))");
            st.execute("CREATE TABLE bjorm_it_converted(id CHAR(36) PRIMARY KEY,amount DECIMAL(18,2),label VARCHAR(255))");
            st.execute("CREATE TABLE bjorm_it_ordinal(id CHAR(36) PRIMARY KEY,status INT,label VARCHAR(255))");
            st.execute("CREATE TABLE bjorm_it_audit(id CHAR(36) PRIMARY KEY,label VARCHAR(255),createdAt TIMESTAMP(6) NULL,updatedAt TIMESTAMP(6) NULL)");
            st.execute("CREATE TABLE bjorm_it_audit_pojo(id CHAR(36) PRIMARY KEY,label VARCHAR(255),createdAt TIMESTAMP(6) NULL,updatedAt TIMESTAMP(6) NULL)");
            st.execute("CREATE TABLE bjorm_it_columns(id CHAR(36) PRIMARY KEY,read_only VARCHAR(255),server_val VARCHAR(255) DEFAULT 'server-default')");
            st.execute("CREATE TABLE bjorm_it_instants(id CHAR(36) PRIMARY KEY,label VARCHAR(255) UNIQUE,occurred_at TIMESTAMP(6) NULL,version INT NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE bjorm_it_documents(id CHAR(36) PRIMARY KEY,payload JSON)");
            st.execute("CREATE TABLE bjorm_it_auto_long(id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,label VARCHAR(255))");
            st.execute("CREATE TABLE bjorm_it_static(id CHAR(36) PRIMARY KEY,label VARCHAR(255))");
            st.execute("CREATE TABLE bjorm_it_explicit_assigned(id BIGINT PRIMARY KEY,label VARCHAR(255))");
            st.execute("CREATE TABLE bjorm_it_explicit_identity(id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,label VARCHAR(255))");
            st.execute("CREATE TABLE bjorm_it_explicit_uuid(id CHAR(36) PRIMARY KEY,label VARCHAR(255))");
            st.execute("CREATE TABLE bjorm_it_orders(id CHAR(36) PRIMARY KEY,description VARCHAR(255),customerId CHAR(36),FOREIGN KEY(customerId) REFERENCES bjorm_it_users(id))");
            st.execute("CREATE TABLE bjorm_it_order_lines(id CHAR(36) PRIMARY KEY,orderId CHAR(36),sku VARCHAR(255),FOREIGN KEY(orderId) REFERENCES bjorm_it_orders(id))");
            st.execute("CREATE TABLE bjorm_it_order_lines_to_one(id CHAR(36) PRIMARY KEY,orderId CHAR(36) NOT NULL,userId CHAR(36),sku VARCHAR(255),FOREIGN KEY(orderId) REFERENCES bjorm_it_orders(id),FOREIGN KEY(userId) REFERENCES bjorm_it_users(id))");
            st.execute("CREATE TABLE bjorm_it_order_notes(id CHAR(36) PRIMARY KEY,orderId CHAR(36),note VARCHAR(255),version INT NOT NULL DEFAULT 0,FOREIGN KEY(orderId) REFERENCES bjorm_it_orders(id))");
        }
        try{
            Bjorm db=Bjorm.open(source,BjormOptions.defaults(),SqlDialects.MYSQL,
                ItUser_BjormMapper.INSTANCE,ItDocument_BjormMapper.INSTANCE,
                AutoLongRecord_BjormMapper.INSTANCE,ItOrder_BjormMapper.INSTANCE,
                ItOrderWithCustomer_BjormMapper.INSTANCE,
                ItOrderLine_BjormMapper.INSTANCE,ItLineWithUser_BjormMapper.INSTANCE,ItOrderWithNestedReference_BjormMapper.INSTANCE,ItOrderNote_BjormMapper.INSTANCE,
                ItInstant_BjormMapper.INSTANCE,MappedColumns_BjormMapper.INSTANCE,OrdinalEvent_BjormMapper.INSTANCE,
                ItAuditRecord_BjormMapper.INSTANCE,ItAuditPojo_BjormMapper.INSTANCE,
                ExplicitAssignedRecord_BjormMapper.INSTANCE,ExplicitIdentityRecord_BjormMapper.INSTANCE,
                ExplicitUuidRecord_BjormMapper.INSTANCE,StaticSchemaRecord_BjormMapper.INSTANCE,
                ItInvoice_BjormMapper.INSTANCE,ItInvoiceInner_BjormMapper.INSTANCE,
                ItInvoicePojo_BjormMapper.INSTANCE,
                ItEmbeddedRecord_BjormMapper.INSTANCE,ItEmbeddedPojo_BjormMapper.INSTANCE,
                ItCompositeRecord_BjormMapper.INSTANCE,ItCompositePojo_BjormMapper.INSTANCE,ItConvertedRecord_BjormMapper.INSTANCE);
            ItUser invoiceCustomer=db.insertReturning(new ItUser(null,"MySQL Invoice Customer",27));
            ItInvoice invoice=db.insertReturning(new ItInvoice(null,invoiceCustomer.id(),"MYSQL-INV",null));
            java.util.concurrent.atomic.AtomicInteger queryCount=new java.util.concurrent.atomic.AtomicInteger();
            Bjorm observed=db.observeSql((operation,duration,ok)->{
                if(operation==SqlOperation.QUERY)queryCount.incrementAndGet();
            });
            check(observed.find(ItInvoice.class,invoice.id()).customer().name().equals("MySQL Invoice Customer"),
                "MySQL ManyToOne record hydrated");
            check(queryCount.get()==1,"MySQL eager parent loaded without N+1 SQL");
            check(db.select(ItInvoice.class).where(ItInvoice_.customerId.eq(invoiceCustomer.id()))
                .orderBy(ItInvoice_.reference.asc()).limit(1).fetch().getFirst().customer()!=null,
                "MySQL ManyToOne sorted paging");
            ItInvoice orphan=db.insertReturning(new ItInvoice(null,null,"MYSQL-NO-CUSTOMER",null));
            check(db.find(ItInvoice.class,orphan.id()).customer()==null,
                "MySQL LEFT ManyToOne retains NULL reference");
            check(db.find(ItInvoiceInner.class,orphan.id())==null,
                "MySQL INNER ManyToOne filters NULL reference");
            ItInvoicePojo invoicePojo=new ItInvoicePojo();
            invoicePojo.setReference("MYSQL-POJO");
            invoicePojo.setCustomerId(invoiceCustomer.id());
            db.insert(invoicePojo);
            check(db.find(ItInvoicePojo.class,invoicePojo.getId()).getCustomer()!=null,
                "MySQL ManyToOne POJO property hydrated");
            ItAddress embeddedAddress=new ItAddress("Rua das Flores","89201");
            ItEmbeddedRecord embedded=db.insertReturning(new ItEmbeddedRecord(null,"mysql-embedded",embeddedAddress));
            check(embeddedAddress.equals(db.find(ItEmbeddedRecord.class,embedded.id()).address()),
                "MySQL flattened embedded record roundtrip");
            check(db.updateBy(ItEmbeddedRecord.class,Map.of("address.zipCode","89220"),
                ItEmbeddedRecord_.id.eq(embedded.id()))==1 &&
                db.find(ItEmbeddedRecord.class,embedded.id()).address().zipCode().equals("89220"),
                "MySQL embedded nested bulk mapped update");
            ItEmbeddedPojo embeddedPojo=new ItEmbeddedPojo();
            embeddedPojo.setLabel("mysql-pojo");
            embeddedPojo.setBilling(embeddedAddress);
            db.insert(embeddedPojo);
            check(db.find(ItEmbeddedPojo.class,embeddedPojo.getId()).getBilling().equals(embeddedAddress),
                "MySQL POJO embedded value roundtrip");
            ItEmbeddedPojo detachedEmbeddedSource=new ItEmbeddedPojo();
            detachedEmbeddedSource.setLabel("mysql-detached-embedded");
            detachedEmbeddedSource.setBilling(embeddedAddress);
            ItEmbeddedPojo detachedEmbeddedSaved=db.insertDetached(detachedEmbeddedSource);
            check(detachedEmbeddedSaved!=detachedEmbeddedSource &&
                detachedEmbeddedSaved.getBilling()!=embeddedAddress &&
                detachedEmbeddedSaved.getBilling().equals(embeddedAddress) &&
                detachedEmbeddedSource.getId()==null &&
                detachedEmbeddedSource.getBilling()==embeddedAddress &&
                detachedEmbeddedSaved.getId()!=null,
                "MySQL detached write creates a separate embedded value snapshot");
            UUID rejectedConvertedId=UUID.randomUUID();
            try {
                db.insertDetached(new ItConvertedRecord(rejectedConvertedId,
                    new MoneyValue(new java.math.BigDecimal("19.00")),"unsafe detached"));
                throw new AssertionError("MySQL detached record with converter was accepted");
            } catch(UnsupportedOperationException expected) {}
            check(db.find(ItConvertedRecord.class,rejectedConvertedId)==null,
                "MySQL fail-closed detached copy must not execute INSERT");
            ItConvertedRecord converted=db.insertReturning(new ItConvertedRecord(
                UUID.randomUUID(),new MoneyValue(new java.math.BigDecimal("22.50")),"convertible"));
            check(db.find(ItConvertedRecord.class,converted.id()).amount().equals(converted.amount()),
                "MySQL generated JDBC converter round-trip");
            check(db.list(ItConvertedRecord.class,
                ItConvertedRecord_.amount.eq(new MoneyValue(new java.math.BigDecimal("22.50")))).size()==1,
                "MySQL typed equality converts domain values");
            check(db.updateBy(ItConvertedRecord.class,
                Map.of("amount",new MoneyValue(new java.math.BigDecimal("31.75"))),
                ItConvertedRecord_.id.eq(converted.id()))==1 &&
                db.find(ItConvertedRecord.class,converted.id()).amount().amount().compareTo(new java.math.BigDecimal("31.75"))==0,
                "MySQL generated converter supports mapped bulk update");
            UUID compositeTenant=UUID.randomUUID();
            CompositeId compoundKey=CompositeId.of(compositeTenant,"SKU-MY");
            ItCompositeRecord record=db.insertReturning(new ItCompositeRecord(compositeTenant,"SKU-MY","first",0));
            check(db.find(ItCompositeRecord.class,compoundKey).equals(record),
                "MySQL composite PK SELECT binds UUID/CHAR + String");
            ItCompositeRecord modified=db.updateReturning(new ItCompositeRecord(compositeTenant,"SKU-MY","updated",0));
            check(modified.version()==1 && modified.label().equals("updated"),
                "MySQL composite PK optimistic update");
            try {db.update(new ItCompositeRecord(compositeTenant,"SKU-MY","stale",0));
                throw new AssertionError("stale composite update accepted");}
            catch(OptimisticLockException expected) {}
            try {db.updateBy(ItCompositeRecord.class,Map.of("tenantId",UUID.randomUUID()),
                ItCompositeRecord_.sku.eq("SKU-MY"));
                throw new AssertionError("MySQL bulk write altered tuple identifier");}
            catch(IllegalArgumentException expected) {}
            check(db.delete(modified)==1 && db.find(ItCompositeRecord.class,compoundKey)==null,
                "MySQL versioned composite PK delete");
            ItCompositePojo tuplePojo=new ItCompositePojo();
            tuplePojo.setTenantId(compositeTenant);tuplePojo.setSku("POJO");tuplePojo.setLabel("test");
            db.insert(tuplePojo);
            check(db.find(ItCompositePojo.class,CompositeId.of(compositeTenant,"POJO")).getLabel().equals("test"),
                "MySQL composite PK POJO read");
            StaticSchemaRecord staticRecord=db.insertReturning(new StaticSchemaRecord(null,"mysql-static"));
            check(db.find(StaticSchemaRecord.class,staticRecord.id()).label().equals("mysql-static"),
                "MySQL static database schema SELECT");
            db.upsertReturning(new StaticSchemaRecord(staticRecord.id(),"mysql-upsert"));
            check(db.find(StaticSchemaRecord.class,staticRecord.id()).label().equals("mysql-upsert"),
                "MySQL static schema native UPSERT");
            ExplicitAssignedRecord assigned=db.insertReturning(new ExplicitAssignedRecord(704L,"assigned"));
            check(db.find(ExplicitAssignedRecord.class,704L).equals(assigned),"MySQL explicit assigned numeric ID");
            ExplicitIdentityRecord identity=db.insertReturning(new ExplicitIdentityRecord(null,"identity"));
            check(identity.id()!=null && identity.id()>0 &&
                db.find(ExplicitIdentityRecord.class,identity.id()).equals(identity),
                "MySQL explicit identity key");
            ExplicitUuidRecord uuidRecord=db.insertReturning(new ExplicitUuidRecord(null,"v7"));
            check(uuidRecord.id()!=null && uuidRecord.id().version()==7 &&
                db.find(ExplicitUuidRecord.class,uuidRecord.id()).equals(uuidRecord),
                "MySQL explicit UUID_V7 key");
            ItAuditRecord audit=db.insertReturning(new ItAuditRecord(null,"mysql-created",null,null));
            ItAuditRecord insertedAudit=db.find(ItAuditRecord.class,audit.id());
            check(insertedAudit.createdAt()!=null && insertedAudit.updatedAt()!=null,
                "MySQL generated audit timestamps");
            ItAuditRecord modifiedAudit=db.updateReturning(insertedAudit);
            check(modifiedAudit.createdAt().equals(insertedAudit.createdAt()) &&
                !modifiedAudit.updatedAt().isBefore(insertedAudit.updatedAt()),
                "MySQL audit update");
            check(db.updateBy(ItAuditRecord.class,Map.of("label","bulk-audit"),ItAuditRecord_.id.eq(audit.id()))==1,
                "MySQL audit bulk auto-stamp");
            ItAuditRecord afterBulkAudit=db.find(ItAuditRecord.class,audit.id());
            check(afterBulkAudit.createdAt().equals(insertedAudit.createdAt()) &&
                !afterBulkAudit.updatedAt().isBefore(modifiedAudit.updatedAt()),
                "MySQL @CreatedAt preserved by bulk update");
            ItAuditRecord upsertedAudit=db.upsertReturning(new ItAuditRecord(audit.id(),
                "mysql-upsert",Instant.parse("2000-01-01T00:00:00Z"),afterBulkAudit.updatedAt()));
            check(upsertedAudit.createdAt().equals(insertedAudit.createdAt()),
                "MySQL UPSERT preserves creation time");
            ItAuditPojo auditPojo=new ItAuditPojo();auditPojo.setLabel("pojo");db.insert(auditPojo);
            check(auditPojo.getCreatedAt()!=null && auditPojo.getUpdatedAt()!=null &&
                db.find(ItAuditPojo.class,auditPojo.getId()).getUpdatedAt()!=null,
                "MySQL POJO generated audit timestamps");
            Instant pojoAuditBefore=auditPojo.getUpdatedAt();
            auditPojo.setLabel("rollback attempt");
            try {
                db.tx(tx->{
                    tx.update(auditPojo);
                    throw new IllegalStateException("rollback MySQL audit");
                });
                throw new AssertionError("Expected MySQL audit rollback");
            } catch(IllegalStateException expected) {}
            check(auditPojo.getUpdatedAt().equals(pojoAuditBefore) &&
                db.find(ItAuditPojo.class,auditPojo.getId()).getLabel().equals("pojo"),
                "MySQL rollback leaves original POJO timestamp unchanged");
            ItAuditPojo refreshedAuditPojo=db.updateReturning(auditPojo);
            check(refreshedAuditPojo.getLabel().equals("rollback attempt") &&
                !refreshedAuditPojo.getUpdatedAt().isBefore(pojoAuditBefore) &&
                auditPojo.getUpdatedAt().equals(pojoAuditBefore),
                "MySQL update stamps JDBC value without mutating caller POJO");
            ItAuditPojo detachedSource=new ItAuditPojo();
            detachedSource.setLabel("detached insert");
            ItAuditPojo detachedPersisted=db.insertDetached(detachedSource);
            check(detachedPersisted!=detachedSource && detachedPersisted.getId()!=null &&
                detachedPersisted.getCreatedAt()!=null && detachedPersisted.getUpdatedAt()!=null &&
                detachedSource.getId()==null && detachedSource.getCreatedAt()==null &&
                detachedSource.getUpdatedAt()==null,
                "MySQL detached INSERT keeps caller unchanged");
            ItAuditPojo duplicateDetached=new ItAuditPojo();
            duplicateDetached.setId(detachedPersisted.getId());
            duplicateDetached.setLabel("duplicate");
            try {
                db.insertDetached(duplicateDetached);
                throw new AssertionError("Expected MySQL duplicate detached INSERT to fail");
            } catch(BjormException expected) {}
            check(duplicateDetached.getCreatedAt()==null && duplicateDetached.getUpdatedAt()==null &&
                db.find(ItAuditPojo.class,detachedPersisted.getId()).getLabel().equals("detached insert"),
                "MySQL detached INSERT failure cannot stamp caller or change stored row");
            ItAuditPojo detachedUpsertSource=new ItAuditPojo();
            detachedUpsertSource.setId(detachedPersisted.getId());
            detachedUpsertSource.setLabel("detached upsert");
            ItAuditPojo detachedUpsertPersisted=db.upsertDetached(detachedUpsertSource);
            check(detachedUpsertPersisted!=detachedUpsertSource &&
                detachedUpsertSource.getUpdatedAt()==null && detachedUpsertSource.getCreatedAt()==null &&
                db.find(ItAuditPojo.class,detachedPersisted.getId()).getLabel().equals("detached upsert"),
                "MySQL detached UPSERT copies timestamps without touching caller");
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
                "MySQL external transaction rollback does not mutate detached caller");
            Instant testTime=Instant.parse("2026-11-10T10:20:30Z");
            ItInstant savedInstant=db.insertReturning(new ItInstant(null,"Original",testTime,0));
            check(testTime.equals(db.find(ItInstant.class,savedInstant.id()).occurredAt()),
                "MySQL generated INSERT/SELECT Instant");
            check(db.update(new ItInstant(savedInstant.id(),"Edited",testTime.plusSeconds(60),0))==1,
                "MySQL generated UPDATE Instant");
            check(testTime.plusSeconds(60).equals(db.find(ItInstant.class,savedInstant.id()).occurredAt()),
                "MySQL generated Instant update roundtrip");
            ItInstant refreshed=db.updateReturning(new ItInstant(savedInstant.id(),"refreshed",testTime,1));
            check(refreshed.version()==2,"MySQL version updateReturning re-read");
            // Versioned MySQL UPSERT: insert and compare-and-swap update are both
            // performed in a transaction. Stale writes must not change the row.
            ItInstant firstUpsert=db.upsertReturning(new ItInstant(null,"upsert-created",testTime,0));
            check(firstUpsert.id()!=null && firstUpsert.version()==0,
                "MySQL versioned UPSERT inserts a previously missing PK");
            ItInstant upsertModified=db.upsertReturning(new ItInstant(savedInstant.id(),"CAS updated",testTime,2));
            check(upsertModified.version()==3 &&
                db.find(ItInstant.class,savedInstant.id()).label().equals("CAS updated"),
                "MySQL versioned UPSERT updates by ID and expected version");
            try {
                db.upsertReturning(new ItInstant(savedInstant.id(),"stale overwrite",testTime,2));
                throw new AssertionError("stale MySQL UPSERT was accepted");
            } catch(OptimisticLockException expected) {}
            check(db.find(ItInstant.class,savedInstant.id()).version()==3 &&
                db.find(ItInstant.class,savedInstant.id()).label().equals("CAS updated"),
                "stale MySQL UPSERT preserves both data and version");
            UUID secondaryConflict=UUID.randomUUID();
            try {
                db.upsertReturning(new ItInstant(secondaryConflict,"CAS updated",testTime,0));
                throw new AssertionError("secondary UNIQUE key should fail");
            } catch(BjormException expected) {
                check(expected.getCause() instanceof SQLException &&
                    "23000".equals(((SQLException)expected.getCause()).getSQLState()),
                    "secondary MySQL UNIQUE conflict remains a SQL integrity error, not stale PK");
            }
            check(db.find(ItInstant.class,secondaryConflict)==null &&
                db.find(ItInstant.class,savedInstant.id()).version()==3,
                "secondary key duplicate never updates unrelated PK");
            CountDownLatch startCas=new CountDownLatch(1);
            AtomicInteger winners=new AtomicInteger();
            AtomicInteger stale=new AtomicInteger();
            try(ExecutorService concurrent=Executors.newFixedThreadPool(2)) {
                List<Future<?>> attempts=new ArrayList<>();
                for(int i=0;i<2;i++) {
                    final int worker=i;
                    attempts.add(concurrent.submit(()->{
                        try {
                            startCas.await();
                            db.upsertReturning(new ItInstant(savedInstant.id(),
                                "winner-"+worker,testTime,3));
                            winners.incrementAndGet();
                        } catch(OptimisticLockException expected) {
                            stale.incrementAndGet();
                        } catch(InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    }));
                }
                startCas.countDown();
                for(Future<?> attempt:attempts)attempt.get();
            }
            check(winners.get()==1 && stale.get()==1 &&
                db.find(ItInstant.class,savedInstant.id()).version()==4,
                "concurrent MySQL CAS permits exactly one update per expected version");
            ItInstant nullTime=db.insertReturning(new ItInstant(null,"Null",null,0));
            check(db.find(ItInstant.class,nullTime.id()).occurredAt()==null,
                "MySQL nullable Instant roundtrip");
            MappedColumns mapped=db.insertReturning(new MappedColumns(null,"read-only","ignored",3,"transient"));
            MappedColumns reloadedCols=db.find(MappedColumns.class,mapped.id());
            check("read-only".equals(reloadedCols.readOnly()) && "server-default".equals(reloadedCols.serverValue()) &&
                reloadedCols.localCounter()==0,"MySQL read-only/generated/transient mapping");
            OrdinalEvent ordinal=db.insertReturning(new OrdinalEvent(null,Status.INACTIVE,"mysql ordinal"));
            check(db.find(OrdinalEvent.class,ordinal.id()).status()==Status.INACTIVE,"MySQL enum ordinal");
            ItUser original=db.insertReturning(new ItUser(null,"MySQL user",33));
            check(original.id()!=null && original.id().version()==7,"v7 generated");
            check(db.find(ItUser.class,original.id()).age()==33,"MySQL UUID read from CHAR(36)");
            UUID streamedId=db.withStream(db.select(ItUser.class).where(ItUser_.id.eq(original.id())),
                rows->rows.findFirst().orElseThrow().id());
            check(streamedId.equals(original.id()),"MySQL resource-scoped mapped Stream roundtrip");
            check(db.updateBy(ItUser.class,Map.of("age",35),ItUser_.id.eq(original.id()))==1,
                "MySQL native updateBy");
            check(db.find(ItUser.class,original.id()).age()==35,"MySQL bulk property roundtrip");
            UUID upsertId=UUID.randomUUID();
            db.batchUpsertReturning(List.of(new ItUser(upsertId,"batch-mysql",30)));
            check(db.find(ItUser.class,upsertId)!=null,"MySQL native batchUpsert");
            db.batchDelete(List.of(new ItUser(upsertId,"batch-mysql",30)));
            check(db.find(ItUser.class,upsertId)==null,"MySQL native batchDelete");
            db.upsertReturning(new ItUser(original.id(),"Updated",34));
            check(db.find(ItUser.class,original.id()).name().equals("Updated"),"MySQL native upsert");
            check(original.id().equals(new ItQueries_Bjorm(db).findUserId(original.id()).orElseThrow()),
                "Native @Query UUID bindings/readers round-trip on MySQL");
            UUID conflictingId=UUID.randomUUID();
            try {
                db.upsertReturning(new ItUser(conflictingId,"Updated",99));
                throw new AssertionError("Secondary UNIQUE conflict must not update another primary-key row");
            } catch(BjormException expected) {
                check(expected.getCause() instanceof SQLException,"MySQL must reject conflicting unique row");
            }
            check(db.find(ItUser.class,original.id()).age()==34 && db.find(ItUser.class,conflictingId)==null,
                "MySQL secondary-key collision cannot mutate unrelated entity");
            check(db.select(ItUser.class).where(ItUser_.id.eq(original.id())).fetch().size()==1,"MySQL UUID predicate bind");
            Instant testInstant=Instant.parse("2026-11-10T10:20:30Z");
            Instant returned=db.one("SELECT ?",
                db.params(testInstant),
                rs->rs.getTimestamp(1).toInstant());
            check(testInstant.equals(returned),"native BJORM Instant binder MySQL timestamp");
            check(db.count(ItUser.class, ItUser_.id.eq(original.id()))==1L,
                "MySQL typed count uses dialect UUID binding");
            check(db.exists(ItUser.class, ItUser_.id.eq(original.id())),
                "MySQL EXISTS uses dialect UUID binding");
            check(!db.exists(ItUser.class, ItUser_.name.eq("nobody")),
                "MySQL EXISTS returns false for missing row");
            db.tx(tx -> {
                check(tx.lockOne(ItUser.class, ItUser_.id.eq(original.id()), LockMode.UPDATE),
                    "MySQL FOR UPDATE works inside transaction");
                check(tx.lockOne(ItUser.class, ItUser_.id.eq(original.id()), LockMode.SHARE),
                    "MySQL FOR SHARE works inside transaction");
            });
            String explicitIsolation=db.txResult(TransactionIsolation.READ_COMMITTED,
                tx->tx.one("SELECT @@transaction_isolation",rs->rs.getString(1)));
            check("READ-COMMITTED".equalsIgnoreCase(explicitIsolation),
                "MySQL honors explicit READ COMMITTED isolation within BJORM transaction");
            db.tx(TransactionIsolation.SERIALIZABLE,tx->
                check(tx.count(ItUser.class,ItUser_.id.eq(original.id()))==1L,
                    "MySQL SERIALIZABLE transaction accepts mapped SQL"));
            ItDocument json=db.insertReturning(new ItDocument(null,"{\"count\":2}"));
            check(db.find(ItDocument.class,json.id()).payload().contains("count"),"MySQL native JSON");
            AutoLongRecord serial=db.insertReturning(new AutoLongRecord(null,"serial"));
            check(serial.id()!=null&&serial.id()>0,"MySQL AUTO_INCREMENT key");
            ItOrderWithCustomer combined=new ItOrderWithCustomer();
            combined.setDescription("mysql combined graph");
            combined.setCustomerId(invoiceCustomer.id());
            ItOrderLine combinedLine=new ItOrderLine();
            combinedLine.setSku("MIXED");
            combined.getLines().add(combinedLine);
            db.insert(combined);
            java.util.concurrent.atomic.AtomicInteger mixedSelects=new java.util.concurrent.atomic.AtomicInteger();
            Bjorm observedMixedGraph=db.observeSql((operation,nanos,ok)->{
                if(operation==SqlOperation.QUERY)mixedSelects.incrementAndGet();
            });
            ItOrderWithCustomer combinedLoaded=observedMixedGraph.find(ItOrderWithCustomer.class,combined.getId());
            check(combinedLoaded.getCustomer()!=null &&
                combinedLoaded.getCustomer().id().equals(invoiceCustomer.id()) &&
                combinedLoaded.getLines().size()==1,
                "MySQL root hydrates Children and ManyToOne in one graph");
            check(mixedSelects.get()==1,"MySQL combined graph has no N+1 queries");
            db.delete(combined);
            check(db.find(ItUser.class,invoiceCustomer.id())!=null,
                "MySQL graph deletion preserves referenced customer");
            ItOrder parent=new ItOrder();parent.setDescription("mysql graph");
            ItOrderLine child=new ItOrderLine();child.setSku("SKU");parent.getLines().add(child);
            db.insert(parent);
            ItOrder loaded=db.find(ItOrder.class,parent.getId());
            check(loaded!=null&&loaded.getLines().size()==1,"MySQL automatic relation JOIN");
            db.delete(parent);
            check(db.find(ItOrder.class,parent.getId())==null,"MySQL cascade delete");
            ItUser nestedUser=db.insertReturning(new ItUser(null,"Nested child customer MySQL",29));
            ItOrderWithNestedReference nestedOrder=db.insertReturning(
                new ItOrderWithNestedReference(UUID.randomUUID(),"nested to-one MySQL",null,
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
                "MySQL nested child @ManyToOne hydration with nullable FK");
            check(nestedQueries.get()==1,
                "MySQL nested child @ManyToOne graph must use one JDBC query");
            // Two distinct real JDBC connections contend for the same row.
            // SKIP LOCKED must return promptly without observing the locked row;
            // NOWAIT must raise the database's documented lock-not-available error.
            try(Connection owner=source.getConnection()) {
                owner.setAutoCommit(false);
                try(PreparedStatement blocker=owner.prepareStatement(
                    "SELECT id FROM bjorm_it_users WHERE id = ? FOR UPDATE")) {
                    blocker.setString(1,invoiceCustomer.id().toString());
                    try(ResultSet held=blocker.executeQuery()) {
                        check(held.next(),"MySQL blocker locked expected user row");
                    }
                }
                for(LockMode mode:List.of(LockMode.UPDATE_SKIP_LOCKED,LockMode.SHARE_SKIP_LOCKED)) {
                    List<ItUser> rows=db.txResult(tx->tx.lockRows(ItUser.class,
                        ItUser_.id.eq(invoiceCustomer.id()),mode,1));
                    check(rows.isEmpty(),"MySQL "+mode+" lockRows must skip a row held by another transaction");
                }
                for(LockMode mode:List.of(LockMode.UPDATE_SKIP_LOCKED,LockMode.SHARE_SKIP_LOCKED)) {
                    boolean selected=db.txResult(tx->
                        tx.lockOne(ItUser.class,ItUser_.id.eq(invoiceCustomer.id()),mode));
                    check(!selected,"MySQL "+mode+" must skip an exclusively locked row");
                }
                for(LockMode mode:List.of(LockMode.UPDATE_NOWAIT,LockMode.SHARE_NOWAIT)) {
                    try {
                        db.tx(tx->tx.lockOne(ItUser.class,ItUser_.id.eq(invoiceCustomer.id()),mode));
                        throw new AssertionError("MySQL NOWAIT unexpectedly acquired a blocked row");
                    } catch(BjormException refused) {
                        Throwable error=refused.getCause();
                        check(error instanceof SQLException && ((SQLException)error).getErrorCode()==3572,
                            "MySQL NOWAIT must report the vendor lock-not-available code");
                    }
                }
                owner.rollback();
            }
            db.tx(tx->{
                List<ItUser> rows=tx.lockRows(ItUser.class,
                    ItUser_.id.eq(invoiceCustomer.id()),LockMode.UPDATE_SKIP_LOCKED,1);
                check(rows.size()==1 && invoiceCustomer.id().equals(rows.getFirst().id()),
                    "MySQL lockRows must return bounded, primary-key-ordered mapped rows");
                try {
                    tx.lockRows(ItUser.class,ItUser_.id.eq(invoiceCustomer.id()),LockMode.UPDATE,0);
                    throw new AssertionError("lockRows accepted zero row limit");
                } catch(IllegalArgumentException expected) {}
            });
            System.out.println("PASS: MySQL 8.4 CRUD, native upsert, JSON, UUID v7, AUTO_INCREMENT and eager JOIN graph");
        }finally {
            try(Connection c=source.getConnection();Statement st=c.createStatement()){
                for(String name:List.of("bjorm_it_converted","bjorm_it_composite","bjorm_it_composite_pojo","bjorm_it_embedded_records","bjorm_it_embedded_pojos","bjorm_it_invoices","bjorm_it_order_notes","bjorm_it_order_lines_to_one","bjorm_it_order_lines","bjorm_it_orders",
                    "bjorm_it_columns","bjorm_it_ordinal","bjorm_it_audit","bjorm_it_audit_pojo",
                    "bjorm_it_explicit_assigned","bjorm_it_explicit_identity","bjorm_it_explicit_uuid",
                    "bjorm_it_static",
                    "bjorm_it_instants","bjorm_it_documents","bjorm_it_auto_long","bjorm_it_users"))
                    st.execute("DROP TABLE IF EXISTS "+name);
            }
        }
    }
}
