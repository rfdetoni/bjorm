package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.Projection;
import java.util.UUID;
@Projection
public record ProductSummary(UUID id,String name) {}
