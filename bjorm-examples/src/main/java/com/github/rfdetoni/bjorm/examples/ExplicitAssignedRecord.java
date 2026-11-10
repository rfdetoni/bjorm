package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.IdStrategy;
import com.github.rfdetoni.bjorm.Table;

@Table("bjorm_it_explicit_assigned")
public record ExplicitAssignedRecord(@Id(strategy=IdStrategy.ASSIGNED) Long id,String label) {}
