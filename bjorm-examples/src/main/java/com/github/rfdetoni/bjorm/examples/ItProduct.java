package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
import java.math.BigDecimal;
@Table("bjorm_it_products")
public record ItProduct(@Id UUID id,String name,@Column("unit_price") BigDecimal price,Status status,@Version int version) {}
