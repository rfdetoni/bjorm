package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.IdStrategy;
import com.github.rfdetoni.bjorm.Table;

@Table("bjorm_it_explicit_identity")
public record ExplicitIdentityRecord(@Id(strategy=IdStrategy.IDENTITY) Long id,String label) {}
