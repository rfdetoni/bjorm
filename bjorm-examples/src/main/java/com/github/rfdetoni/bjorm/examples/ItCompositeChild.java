package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

@Table("bjorm_it_composite_children")
public record ItCompositeChild(@Id UUID id, UUID tenantId, String sku, String label) {}
