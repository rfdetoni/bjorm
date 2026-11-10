package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.EnumOrdinal;
import com.github.rfdetoni.bjorm.Id;
import com.github.rfdetoni.bjorm.Table;
import java.util.UUID;
@Table("bjorm_it_ordinal")
public record OrdinalEvent(@Id UUID id,@EnumOrdinal Status status,String label) {}
