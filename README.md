# BJORM — Bare Metal Java ORM

**Experimental 0.3.4-SNAPSHOT** — a minimal, compile-time-assisted relational mapper for Java 25. Zero Spring/JPA/Hibernate dependencies in the core; SQL and JDBC remain visible and under application control.

> Early-stage source implementation. **Not production-ready**. It is not published to Maven Central. See [architecture and acceptance status](ARCHITECTURE.md).

## Philosophy

- Plain JDBC (`DataSource`, `PreparedStatement`, `ResultSet`) and no per-request reflection or dynamic proxy.
- Generated SQL, generated positional readers/binders, immutable mapper instances, explicit instance registration.
- Optional Active Record and a small typed query DSL; compiled `@Query` repositories for complex SQL including explicit joins.
- No framework-enforced session, identity map, dirty checking, implicit transaction propagation, global manager, or JPA API.
- Predictable allocations; use `forEach` to avoid loading large result sets, and batch writes for bulk workloads.

## Build

Requires JDK **25** and Maven 3.9+:

```shell
mvn -B clean verify
./scripts/verify.sh
```

The portable `verify.sh` also compiles and executes the source on Java 21 for smoke testing (it does **not** validate a JDK 25 Maven build).

## Usage: POJO, Active Record, DSL

```java
@Table("products")
public class Product implements ActiveRecord<Product> {
    @Id private UUID id;
    private String name;
    @Column("unit_price") private BigDecimal price;
    @Version private int version;
    public Product() {}
    // public getters and setters for all fields
}

// The annotation processor generates Product_BjormMapper, Product_ and a ServiceLoader index.
var db = Bjorm.open(dataSource);    // discover generated mappers once at startup
// Or: Bjorm.open(dataSource, Product_BjormMapper.INSTANCE) for explicit registration.
Product p = new Product();
p.setId(UUID.randomUUID());
p.setName("Pencil");
p.insert(db);               // Optional ActiveRecord; explicit database context.
db.update(p);               // Version-controlled updates validate affected rows.
var found = db.find(Product.class, p.getId());
var list = db.select(Product.class)
        .where(Product_.name.in(List.of("Pencil", "Pen")))
        .orderBy(Product_.name.asc())
        .limit(20)
        .fetch();

db.tx(tx -> {
    tx.insert(anotherProduct);  // transaction-scoped CRUD and SQL
    tx.execute("UPDATE audit SET events = events + 1", StatementBinder.NONE);
});
```

## Find one and fetch only requested properties

`findOne` returns `Optional<T>` and issues `LIMIT 1` at the database; no matching row yields `Optional.empty()`.

```java
Optional<Product> first = db.findOne(Product.class, Product_.name.eq("Pencil"));
Optional<Product> latest = db.select(Product.class)
    .orderBy(Product_.name.desc()).first();
```

Use **Java property names**, not SQL column names. The processor compiles the mapping: `"price"` becomes `unit_price` for `@Column("unit_price")`. A mistaken or malicious property name fails validation before SQL is executed. The map returned by `findFields` is keyed by Java property names, keeps SQL `NULL` values, and **never creates partially initialized entities**.

```java
List<Map<String,Object>> products = db.findFields(
    Product.class, Product_.name.eq("Pencil"), "id", "name", "price"
);
// SELECT id, name, unit_price FROM products WHERE name = ?

Optional<Map<String,Object>> one = db.findOneFields(
    Product.class, Product_.name.eq("Pencil"), "name", "price"
);

var ordered = db.select(Product.class).as("p")
    .fields("name", "price")
    .where(Product_.name.as("p").like("P%"))
    .orderBy(Product_.name.as("p").asc())
    .limit(20).fetch();
```

For one scalar column, avoid allocating row maps:

```java
List<String> names = db.findColumn(Product.class, "name", String.class,
    Product_.price.gt(new BigDecimal("10.00")));
Optional<String> name = db.findColumnOne(Product.class, "name", String.class,
    Product_.name.eq("Pencil"));
// DSL alternative: db.select(Product.class).fields("name").values(String.class);
```

For **multiple typed fields without maps**, use the generated `@Projection` record reader with the requested property names **in the same positional order** as the record components:

```java
@Projection public record ProductSummary(UUID id, String name) {}

List<ProductSummary> summaries = db.select(Product.class)
    .where(Product_.name.eq("Pencil"))
    .fields("id", "name")
    .fetch(ProductSummary_BjormRowMapper.INSTANCE);

Optional<ProductSummary> summary = db.select(Product.class)
    .fields("id", "name")
    .first(ProductSummary_BjormRowMapper.INSTANCE);
```

The `db` and `tx` operations expose the same methods and share the same JDBC engine; `tx.findOne`, `tx.findFields` and `tx.findColumn` use the transaction's connection. `first()` means the first matching row, **not** a uniqueness assertion. Use `orderBy` when the first row must be deterministic. Both query builders are mutable and should be treated as single-use.

## Compile-time repositories and projections

```java
@Projection public record ProductSummary(UUID id, String name) {}

public interface ProductQueries {
    @Query("SELECT id, name FROM products WHERE name = :name")
    List<ProductSummary> byName(@Param("name") String name);
}

ProductQueries queries = new ProductQueries_Bjorm(db);
List<ProductSummary> summaries = queries.byName("Pencil");
// Pass tx instead of db to run the generated repository inside an explicit transaction.
```

The processor converts named parameters into JDBC `?` and generates positional bindings. It skips literal strings, comments, double-quoted identifiers, PostgreSQL `::` casts, and dollar-quoted strings. This is **named-parameter compilation**, not full SQL syntax or schema validation. For static joins and tailored projections, write ordinary SQL in `@Query`; the output has no runtime proxy.

## Performance-oriented features

- POJOs and public records; String, UUID, primitive/wrapper numeric/boolean, BigDecimal, supported `java.time` types and Java enums.
- `@Id(generated=true)` on mutable POJOs uses JDBC `RETURN_GENERATED_KEYS` (batch generated IDs deliberately not supported).
- `@Version` on int/long columns uses `UPDATE ... SET version = version+1 WHERE id=? AND version=?` and conditional delete. **Re-read the entity after successful updates:** versions are not mutated automatically, especially on records.
- `db.batchInsert` and `batchUpdate` use up to 256 rows per JDBC batch within one transaction; version-controlled unknown batch counts are rejected instead of assuming successful locking.
- `db.forEach` runs in an explicit transaction with fetch size 128 for PostgreSQL streaming. `db.scan(sql, binder, generatedProjectionMapper, consumer)` streams native projections. Resources close before the scan returns. Do not keep row cursors outside the callback.
- Explicit DSL joins are supported: `db.select(Product.class).as("p").join(User.class, "u", Product_.id.as("p").sameAs(User_.id.as("u"))).fetch()`; aliases and columns are validated, and ON parameters precede WHERE parameters.
- `@Json String payload` generates `CAST(? AS jsonb)` for PostgreSQL; callers provide serialized JSON. No JSON codec dependency in core.
- Call `db.query(sql, binder, mapper)` for manually optimized SQL; map DTOs using generated `@Projection` readers.

## Optional Spring Boot 4 integration

`bjorm-spring-boot` is an **optional** integration, not a core dependency. Include the module plus your usual Spring JDBC/DataSource configuration and register generated mapper beans:

```java
@Configuration
class BjormMappings {
    @Bean EntityMapper<Product> productMapper() {
        return Product_BjormMapper.INSTANCE;
    }
}
```

The adapter creates the `Bjorm` bean using `TransactionAwareDataSourceProxy`; if no mapper beans are registered, it discovers generated mappers via ServiceLoader. calls to injected `Bjorm` from a Spring `@Transactional` method participate in Spring-managed connections. Do **not** nest `db.tx` within Spring-managed transactions; use the Spring transaction boundary or BJORM's explicit transactions, not both at once. The adapter has not been integration-tested against a running Spring Boot context in the present environment.

## Tests and benchmarks

- `scripts/verify.sh`: main smoke, advanced generated-code smoke, compile-time mapping checks, zero-framework core. All run without a database.
- `PostgresIntegrationTest`: opt-in **only in a disposable database**; creates and drops `bjorm_it_*` fixture tables. Supply `BJORM_TEST_JDBC_URL`, `BJORM_TEST_USER`, `BJORM_TEST_PASSWORD`, `BJORM_DRIVER_JAR` and execute `scripts/verify.sh`.
- `bjorm-benchmarks`: JMH microbenchmark against equivalent manual mapping with a simulated ResultSet, **not** proof of real JDBC/database performance; details in its README.
- Run real PostgreSQL JDBC/manual versus BJORM benchmarks with identical SQL, dataset, connection pool, transactions and concurrency before making performance claims.

## Design boundaries

No transparent lazy loading, L2 cache, implicit graph persistence, schema migrations, complete SQL parser or fallback reflective mapping. SQL table/column names are ASCII identifiers and not quoted; avoid reserved names. `@Projection` maps columns **in selection order**. Static `@Query` methods can return `List<T>`, `Optional<T>`, `T`, a supported scalar, or `int` for DML; there is no proxy-based fallback. Plain `db.list()` collects all results, so prefer `limit` or `forEach` for larger datasets.

See [ARCHITECTURE.md](ARCHITECTURE.md) and [`bjorm-examples`](bjorm-examples/src/main/java/com/github/rfdetoni/bjorm/examples).


## Maven versioning and publication

The canonical development branch is **`main`**. All code changes should be pushed to `main`; the GitHub Actions workflows handle Maven packages:

- **CI** (`.github/workflows/build.yml`): compiles the Maven reactor on JDK 25 and runs critical checks.
- **Snapshot publication** (`publish-snapshot.yml`): on every push to `main`, validates the project and deploys `bjorm-core`, `bjorm-processor`, `bjorm-spring-boot` plus their parent POM to **GitHub Packages**. Examples and benchmarks are not published.
- **Stable release** (`release.yml`): manually dispatch from `main` with a version matching the currently committed snapshot (e.g., `0.3.3` for `0.3.3-SNAPSHOT`). The workflow updates every POM, verifies, commits and tags `v0.3.3`, deploys Maven artifacts, then moves `main` to `0.3.4-SNAPSHOT`. All publication uses the workflow's `GITHUB_TOKEN` and no custom credentials are required in the repository.

### Release procedure

1. Make sure the latest changes have been pushed to `main` and CI passes.
2. In **GitHub → Actions → release-maven → Run workflow**, select `main` and enter the stable version, e.g. `0.3.3`.
3. Check that the release version commit and tag exist, the library packages were published, and `main` has the next snapshot version.

GitHub Actions must have **read/write workflow permissions**, and branch protection must permit the release workflow's pushes. The workflow uses the built-in token with `contents:write` and `packages:write` permissions. A failed deploy after the release tag is pushed requires explicit recovery; it does not mean the release was published successfully. Version checks prevent accidental mismatched reactor versions.

### Maven consumer example (GitHub Packages)

JDBC `javax.sql.DataSource` remains part of Java SE 25 (`java.sql` module), and is not a Jakarta EE API; `jakarta.sql.DataSource` does not exist in the JDK. The annotation processor uses `javax.annotation.processing` from the Java SE `java.compiler` module.

The Maven `groupId` and API package are `com.github.rfdetoni.bjorm`; GitHub Packages remains at `https://maven.pkg.github.com/rfdetoni/bjorm`. Maven Central publication requires separate namespace verification.


```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/rfdetoni/bjorm</url>
  </repository>
</repositories>
<dependencies>
  <dependency>
    <groupId>com.github.rfdetoni.bjorm</groupId>
    <artifactId>bjorm-core</artifactId>
    <version>0.3.3-SNAPSHOT</version>
  </dependency>
</dependencies>
```

For consumers outside GitHub Actions, GitHub Packages may require authenticated access; configure `settings.xml` with a token in your own environment, **never commit credentials**. The configured target is **GitHub Packages**, not Maven Central. Maven Central publication requires separately verifying the namespace and configuring Central Portal publishing/signing credentials.
