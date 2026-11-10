package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.CreatedAt;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import com.github.rfdetoni.bjorm.UpdatedAt;
import java.time.Instant;
import java.util.UUID;

@Table("bjorm_it_audit_pojo")
public class ItAuditPojo {
    @Id private UUID id;
    private String label;
    @CreatedAt private Instant createdAt;
    @UpdatedAt private Instant updatedAt;

    public ItAuditPojo(){}
    public UUID getId(){return id;}
    public void setId(UUID value){id=value;}
    public String getLabel(){return label;}
    public void setLabel(String value){label=value;}
    public Instant getCreatedAt(){return createdAt;}
    public void setCreatedAt(Instant value){createdAt=value;}
    public Instant getUpdatedAt(){return updatedAt;}
    public void setUpdatedAt(Instant value){updatedAt=value;}
}
