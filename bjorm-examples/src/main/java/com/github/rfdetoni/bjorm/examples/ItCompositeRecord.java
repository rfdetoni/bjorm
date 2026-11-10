package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.*;
import java.util.UUID;

@Table("bjorm_it_composite")
public record ItCompositeRecord(
    @Id(strategy=IdStrategy.ASSIGNED) UUID tenantId,
    @Id(strategy=IdStrategy.ASSIGNED) String sku,
    String label,
    @Version int version
) {}
