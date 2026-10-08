package dev.bjorm.bench;
import dev.bjorm.*;
import java.util.UUID;
@Table("bjorm_bench")
public record BenchItem(@Id UUID id,String label,int quantity) {}
