package dev.bjorm.examples;
import dev.bjorm.*;
import java.util.*;
public interface ItQueries {
    @Query("SELECT p.id, p.name FROM bjorm_it_products p JOIN bjorm_it_users u ON p.id = u.id WHERE u.name = :name")
    List<ProductSummary> productsByUserName(@Param("name") String name);
}
