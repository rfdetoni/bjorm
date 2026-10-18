package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;

@Table("bjorm_it_shared_leaves")
public record ItSharedLeaf(@Id UUID id, UUID branchId, String label) {}
