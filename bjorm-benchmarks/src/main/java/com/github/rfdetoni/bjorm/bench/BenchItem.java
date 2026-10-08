package com.github.rfdetoni.bjorm.bench;
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table("bjorm_bench")
public record BenchItem(@Id UUID id,String label,int quantity) {}
