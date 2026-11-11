package com.github.rfdetoni.bjorm.bench;

import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;

@Table("bjorm_bench_children")
public record BenchChild(@Id UUID id, UUID parentId, String label) {}
