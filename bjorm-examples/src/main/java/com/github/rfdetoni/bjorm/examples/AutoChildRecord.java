package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table("bjorm_it_record_children")
public record AutoChildRecord(@Id UUID id, UUID parentId, String label) {}
