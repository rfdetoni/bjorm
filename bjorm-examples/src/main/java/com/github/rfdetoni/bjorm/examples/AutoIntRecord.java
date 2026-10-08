package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
@Table("bjorm_it_auto_int")
public record AutoIntRecord(@Id int id, String label) {}
