package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table("bjorm_it_documents")
public record ItDocument(@Id UUID id,@Json String payload) {}
