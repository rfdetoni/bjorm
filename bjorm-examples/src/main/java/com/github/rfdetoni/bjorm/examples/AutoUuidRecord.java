package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table("bjorm_it_auto_uuid")
public record AutoUuidRecord(@Id UUID id, String label) implements ActiveRecord<AutoUuidRecord> {}
