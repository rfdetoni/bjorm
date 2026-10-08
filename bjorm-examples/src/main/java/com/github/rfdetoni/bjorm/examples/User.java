package com.github.rfdetoni.bjorm.examples;
import com.github.rfdetoni.bjorm.*;
import java.util.UUID;
@Table("users")
public record User(@Id UUID id, String name, int age) implements ActiveRecord<User> {}
