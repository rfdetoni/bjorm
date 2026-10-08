package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.time.LocalDateTime;
import java.util.UUID;
public class AuditedBase {
    @Id private UUID id;
    @Column("created_at") private LocalDateTime createdAt;
    public UUID getId(){return id;}
    public void setId(UUID id){this.id=id;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
