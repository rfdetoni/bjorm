package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.ManyToOne;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

@Table("bjorm_it_order_lines_to_one")
public record ItLineWithUser(@Id UUID id, UUID orderId, UUID userId,
                             String sku, @ManyToOne(mappedBy="userId") ItUser customer) {}
