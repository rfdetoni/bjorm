package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.List;
import java.util.UUID;

@Table("bjorm_it_inherited_fk_parents")
public record ItInheritedFkParent(@Id UUID id, String name,
    @Children(mappedBy="parentId") List<ItInheritedFkChild> children) {}
