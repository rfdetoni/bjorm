package dev.bjorm.examples;
import dev.bjorm.*;
import java.util.UUID;
@Table("documents")
public record JsonDocument(@Id UUID id,@Json String payload) {}
