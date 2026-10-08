package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table("documents")
public record JsonDocument(@Id UUID id,@Json String payload) {}
