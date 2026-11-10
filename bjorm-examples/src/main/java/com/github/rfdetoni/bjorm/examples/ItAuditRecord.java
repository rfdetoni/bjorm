package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.CreatedAt;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import com.github.rfdetoni.bjorm.UpdatedAt;
import java.time.Instant;
import java.util.UUID;

@Table("bjorm_it_audit")
public record ItAuditRecord(@Id UUID id,String label,
    @CreatedAt Instant createdAt,@UpdatedAt Instant updatedAt) {}
