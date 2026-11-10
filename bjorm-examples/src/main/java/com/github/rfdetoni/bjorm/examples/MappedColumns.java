package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Column;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import com.github.rfdetoni.bjorm.Transient;
import java.util.UUID;

/** Read-only persisted columns, database-populated values and unmapped record state. */
@Table("bjorm_it_columns")
public record MappedColumns(
    @Id UUID id,
    @Column(value="read_only",updatable=false) String readOnly,
    @Column(value="server_val",insertable=false) String serverValue,
    @Transient int localCounter,
    @Transient String localHint
) {}
