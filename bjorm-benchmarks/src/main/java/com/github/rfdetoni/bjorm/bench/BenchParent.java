package com.github.rfdetoni.bjorm.bench;

import com.github.rfdetoni.bjorm.Children;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import java.util.List;
import java.util.UUID;

@Table("bjorm_bench_parents")
public record BenchParent(@Id UUID id, String label,
                          @Children(mappedBy="parentId") List<BenchChild> children) {}
