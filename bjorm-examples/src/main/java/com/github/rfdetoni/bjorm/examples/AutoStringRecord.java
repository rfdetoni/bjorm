package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
@Table("bjorm_it_auto_string")
public record AutoStringRecord(@Id String id, String label) {}
