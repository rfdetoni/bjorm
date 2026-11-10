package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Column;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import com.github.rfdetoni.bjorm.Version;
import java.time.Instant;
import java.util.UUID;

/** Generated INSERT/UPDATE/SELECT must handle JDBC Instant without setObject(Instant). */
@Table("bjorm_it_instants")
public record ItInstant(@Id UUID id, String label,
    @Column("occurred_at") Instant occurredAt, @Version int version) {}
