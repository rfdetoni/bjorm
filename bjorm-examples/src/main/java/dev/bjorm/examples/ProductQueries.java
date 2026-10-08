package dev.bjorm.examples;
import dev.bjorm.*;
import java.util.*;
/** Stateless implementation is generated at build time (no dynamic proxy). */
public interface ProductQueries {
    @Query("SELECT id, name FROM products WHERE name = :name AND id <> :excluded")
    List<ProductSummary> summaries(@Param("name") String name,@Param("excluded") UUID excluded);

    @Query("SELECT id, name, unit_price, status, version FROM products WHERE id = :id")
    Optional<Product> byId(@Param("id") UUID id);

    @Query("SELECT count(*) FROM products WHERE name = :name")
    long countByName(@Param("name") String name);

    @Query("UPDATE products SET name = :name WHERE id = :id")
    int rename(@Param("name") String name,@Param("id") UUID id);

    @Query("SELECT id, name FROM products WHERE name = ':not_a_parameter' AND id <> :excluded::uuid -- :ignored\n")
    List<ProductSummary> literalSafety(@Param("excluded") UUID excluded);
}
