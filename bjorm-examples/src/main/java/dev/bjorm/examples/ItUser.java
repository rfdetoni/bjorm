package dev.bjorm.examples;
import dev.bjorm.*;
import java.util.UUID;
@Table("bjorm_it_users")
public record ItUser(@Id UUID id,String name,int age) {}
