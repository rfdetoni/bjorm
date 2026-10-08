package dev.bjorm.examples;
import dev.bjorm.*;
import java.util.UUID;
@Table("bjorm_it_documents")
public record ItDocument(@Id UUID id,@Json String payload) {}
