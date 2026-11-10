package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Embedded;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

@Table("bjorm_it_embedded_records")
public record ItEmbeddedRecord(@Id UUID id,String label,
    @Embedded(prefix="shipping_") ItAddress address) {}
