package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
@Table("bjorm_it_auto_long")
public record AutoLongRecord(@Id Long id, String label) {}
