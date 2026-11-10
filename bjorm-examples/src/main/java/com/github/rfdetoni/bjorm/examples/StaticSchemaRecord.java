package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

/** Fixed and compile-time validated schema; forbidden in dynamic tenant contexts. */
@Table(value="bjorm_it_static",schema="bjorm_test")
public record StaticSchemaRecord(@Id UUID id,String label) {}
