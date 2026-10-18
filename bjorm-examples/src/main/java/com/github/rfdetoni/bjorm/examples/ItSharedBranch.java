package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.List;
import java.util.UUID;

@Table("bjorm_it_shared_branches")
public record ItSharedBranch(@Id UUID id, UUID primaryRootId, UUID backupRootId,
    @Children(mappedBy="branchId") List<ItSharedLeaf> leaves) {}
