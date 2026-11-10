package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Bjorm;
import com.github.rfdetoni.bjorm.OptimisticLockException;
import com.github.rfdetoni.bjorm.SqlDialects;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.sql.ResultSet;

/** Verify timestamp lifecycles produced entirely by the annotation processor. */
public final class AuditSmokeTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        var recordMapper=ItAuditRecord_BjormMapper.INSTANCE;
        ItAuditRecord initial=new ItAuditRecord(null,"new",null,null);
        ItAuditRecord inserted=recordMapper.materializeInsert(initial);
        check(initial.id()==null && initial.createdAt()==null,"records must remain immutable");
        check(inserted.id()!=null && inserted.id().version()==7,"generated immutable ID");
        check(inserted.createdAt()!=null && inserted.updatedAt()!=null,"insert audit timestamps populated");
        check(recordMapper.updateSql().contains("updatedAt = ?") &&
              !recordMapper.updateSql().contains("createdAt = ?"),"createdAt never updated");
        check(recordMapper.upsertSql().contains("updatedAt") &&
              !recordMapper.upsertSql().contains("createdAt=EXCLUDED"),"UPSERT preserves creation time");
        ItAuditRecord altered=recordMapper.materializeUpdate(inserted);
        check(altered!=inserted && altered.createdAt().equals(inserted.createdAt()) &&
              !altered.updatedAt().isBefore(inserted.updatedAt()),"update stamps a new immutable record");
        ItAuditRecord changed=recordMapper.materializeUpsert(initial);
        check(changed.id()!=null && changed.createdAt()!=null && changed.updatedAt()!=null,
            "upsert stamps fresh audit record exactly once");
        check("updatedAt".equals(recordMapper.updatedAtProperty()) &&
              recordMapper.updatedAtNow() instanceof Instant,"auto timestamp bulk API");
        ItAuditPojo pojo=new ItAuditPojo();pojo.setLabel("mutable");
        ItAuditPojo same=ItAuditPojo_BjormMapper.INSTANCE.materializeInsert(pojo);
        check(same==pojo && pojo.getId()!=null && pojo.getCreatedAt()!=null &&
              pojo.getUpdatedAt()!=null,"POJO insert audit fields and v7 key");
        Instant before=pojo.getCreatedAt();
        Instant beforeUpdated=pojo.getUpdatedAt();
        ItAuditPojo prepared=ItAuditPojo_BjormMapper.INSTANCE.materializeUpdate(pojo);
        check(prepared==pojo && pojo.getCreatedAt().equals(before) &&
              pojo.getUpdatedAt().equals(beforeUpdated),
            "POJO update materializer never modifies caller state before JDBC commit");
        ItAuditPojo caller=new ItAuditPojo();
        caller.setLabel("snapshot");
        ItAuditPojo detached=ItAuditPojo_BjormMapper.INSTANCE.copyForWrite(caller);
        check(detached!=caller && "snapshot".equals(detached.getLabel()) &&
              detached.getId()==null && detached.getCreatedAt()==null,
            "generated POJO detached copy preserves fields but not object identity");
        ItAuditPojo stamped=ItAuditPojo_BjormMapper.INSTANCE.materializeInsert(detached);
        check(stamped.getId()!=null && stamped.getCreatedAt()!=null &&
              stamped.getUpdatedAt()!=null && caller.getId()==null &&
              caller.getCreatedAt()==null && caller.getUpdatedAt()==null,
            "stamping a detached copy never mutates caller-owned audit fields");
        ItAuditPojo detachedUpsert=ItAuditPojo_BjormMapper.INSTANCE.copyForWrite(caller);
        ItAuditPojo_BjormMapper.INSTANCE.materializeUpsert(detachedUpsert);
        check(caller.getUpdatedAt()==null && detachedUpsert.getUpdatedAt()!=null,
            "UPSERT stamps a copied POJO, not the caller");
        System.out.println("PASS: audit timestamps for generated records and POJOs");
    }
}
