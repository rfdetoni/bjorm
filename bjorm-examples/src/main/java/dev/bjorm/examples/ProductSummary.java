package dev.bjorm.examples;
import dev.bjorm.Projection;
import java.util.UUID;
@Projection
public record ProductSummary(UUID id,String name) {}
