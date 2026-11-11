package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Children;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import java.util.List;
import java.util.UUID;

/** Root with a child collection whose members each reference a flat parent entity. */
@Table("bjorm_it_orders")
public record ItOrderWithNestedReference(@Id UUID id, String description,
    UUID customerId, @Children(mappedBy="orderId") List<ItLineWithUser> lines) {}
