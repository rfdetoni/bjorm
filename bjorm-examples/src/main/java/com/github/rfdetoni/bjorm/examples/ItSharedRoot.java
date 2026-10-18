package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.List;
import java.util.UUID;

@Table("bjorm_it_shared_roots")
public record ItSharedRoot(@Id UUID id, String title,
    @Children(mappedBy="primaryRootId") List<ItSharedBranch> primary,
    @Children(mappedBy="backupRootId") List<ItSharedBranch> backup) {}
