package dev.bjorm.examples;
import dev.bjorm.*;
import java.util.UUID;
@Table("users")
public record User(@Id UUID id, String name, int age) implements ActiveRecord<User> {}
