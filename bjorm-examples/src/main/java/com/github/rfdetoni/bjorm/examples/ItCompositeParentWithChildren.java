package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Children;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.IdStrategy;
import com.github.rfdetoni.bjorm.Table;
import java.util.List;
import java.util.UUID;

/** Parent @Id is (tenantId,sku), child FK must always use both components. */
@Table("bjorm_it_composite")
public record ItCompositeParentWithChildren(
    @Id(strategy=IdStrategy.ASSIGNED) UUID tenantId,
    @Id(strategy=IdStrategy.ASSIGNED) String sku,
    String label,
    @Children(mappedByParts={"tenantId","sku"}) List<ItCompositeChild> children
) {}
