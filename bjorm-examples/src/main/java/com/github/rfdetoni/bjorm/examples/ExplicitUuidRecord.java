package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.IdStrategy;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

@Table("bjorm_it_explicit_uuid")
public record ExplicitUuidRecord(@Id(strategy=IdStrategy.UUID_V7) UUID id,String label) {}
