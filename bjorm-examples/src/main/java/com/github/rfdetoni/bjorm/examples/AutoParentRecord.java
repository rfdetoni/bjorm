package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.*;
@Table("bjorm_it_record_parents")
public record AutoParentRecord(@Id UUID id, String label, @Children(mappedBy="parentId") List<AutoChildRecord> children) {}
