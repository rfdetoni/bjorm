package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.ManyToOne;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

/** The FK's two columns must match BOTH target primary-key columns. */
@Table("bjorm_it_composite_refs")
public record ItCompositeReference(
    @Id UUID id,
    UUID tenantId,
    String sku,
    String label,
    @ManyToOne(mappedByParts={"tenantId","sku"}) ItCompositeRecord product
) {}
