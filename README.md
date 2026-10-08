# BJORM — Bare Metal Java ORM

**Experimental 0.3.10-SNAPSHOT** — a minimal, compile-time-assisted relational mapper for Java 25. Zero Spring/JPA/Hibernate dependencies in the core; SQL and JDBC remain visible and under application control.

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
- `@Id` on `UUID` automatically creates UUID v7, and `@Id` on `String` creates its RFC UUID v7 text representation when null.
- `@Id` on `int`, `long`, `Integer` or `Long` uses the database identity column (`GENERATED ... AS IDENTITY`) and JDBC generated keys. Identity inserts omit the ID column.
- Explicit caller-managed identifiers use `@Id(assigned=true)`; `@Id(generated=true)` and `@Id(uuidV7=true)` remain available for explicit intent.
- For immutable Java `record`s, use `db.insertReturning(record)` / `db.upsertReturning(record)` / `db.batchInsertReturning(records)` to receive new instances with generated IDs. The void `insert(record)` intentionally refuses a missing ID.
- For POJOs, `db.insert(pojo)` assigns client-generated IDs via the public setter. `batchInsert(pojos)` also fills automatic client-side IDs. Batch inserts for database-generated numeric IDs use `batchInsertReturning` (individual inserts for generated keys).
- All ID generation and record reconstruction are emitted at compile time; no entity reflection on the critical JDBC path.
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
- **Stable release** (`release.yml`): manually dispatch from `main` with a version matching the currently committed snapshot (e.g., `0.3.3` for `0.3.3-SNAPSHOT`). The workflow updates every POM, verifies, commits and tags `v0.3.3`, deploys Maven artifacts, then moves `main` to `0.3.8-SNAPSHOT`. All publication uses the workflow's `GITHUB_TOKEN` and no custom credentials are required in the repository.

### Release procedure

1. Make sure the latest changes have been pushed to `main` and CI passes.
2. In **GitHub → Actions → release-maven → Run workflow**, select `main` and enter the stable version, e.g. `0.3.3`.
3. Check that the release version commit and tag exist, the library packages were published, and `main` has the next snapshot version.

GitHub Actions must have **read/write workflow permissions**, and branch protection must permit the release workflow's pushes. The workflow uses the built-in token with `contents:write` and `packages:write` permissions. A failed deploy after the release tag is pushed requires explicit recovery; it does not mean the release was published successfully. Version checks prevent accidental mismatched reactor versions.

### Maven consumer example (GitHub Packages)

JDBC `javax.sql.DataSource` remains part of the Java SE 25 `java.sql` module: it is **not** a legacy Jakarta EE API and must not be replaced by a nonexistent `jakarta.sql.DataSource`. Similarly, the annotation processor uses `javax.annotation.processing` from the JDK `java.compiler` module.

The published Maven `groupId` and the Java API namespace are `com.github.rfdetoni.bjorm`. The GitHub Packages repository URL remains `https://maven.pkg.github.com/rfdetoni/bjorm`. Maven coordinates are not automatically derived from the GitHub URL; the declared namespace must be consistent across modules. Maven Central requires independent namespace verification.

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

## UUID v7, upsert and nested persistence (PostgreSQL)

`@Id` automatically infers its ID strategy from the Java type. For `UUID`, BJORM generates RFC 9562 UUID v7; for `String`, it generates UUID v7 text; for numeric IDs (`int`, `long` and their wrappers), the database must define an identity column and BJORM retrieves it using JDBC generated keys.

A mutable POJO receives a generated ID through its setter; a Java `record` is immutable, so use `insertReturning(...)` to receive a new record instance. The original record is never modified.

```java
@Table("users")
public record User(@Id UUID id, String name) {}
User saved = db.insertReturning(new User(null, "Alice")); // saved.id().version() == 7

@Table("legacy_numbers")
public record Numbered(@Id Long id, String name) {}
Numbered savedNumber = db.insertReturning(new Numbered(null, "Example"));
// PostgreSQL: id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY
```

For manually assigned IDs use `@Id(assigned=true)`. Client-generated identifiers preserve explicit non-null IDs rather than replacing them. For batches of immutable entities use `batchInsertReturning`, which returns a new list with populated IDs. Numeric identity batches use per-row inserts to recover their keys reliably.

```java
@Table("orders")
public class Order {
    @Id(uuidV7 = true) private UUID id;
    private String customer;
    @Children(mappedBy = "orderId") private List<OrderLine> lines;
    // public no-arg constructor + getters and setters
}

@Table("order_lines")
public class OrderLine {
    @Id(uuidV7 = true) private UUID id;
    private UUID orderId; // child FK; must match parent ID type
    private String sku;
    // public no-arg constructor + getters and setters
}

var db = Bjorm.open(dataSource);
db.insert(order);     // one transaction: parent, then children; FKs assigned
// Or update existing rows and insert new ones using native PostgreSQL ON CONFLICT:
db.upsert(order);
db.delete(order);     // one transaction: delete persisted children, then parent
```

- Child relations are explicit `@Children(mappedBy="childForeignKeyJavaProperty")` lists/collections. Only mapped columns are persisted; full-entity SELECTs eagerly load @Children with generated JOIN SQL.
- The parent ID is generated before saving children. Record children with null foreign keys are copied with the parent's ID; `insertReturning` returns the updated graph containing new record IDs. Conflicting non-null foreign keys are rejected.
- All graph writes run in a single transaction; use `insertReturning` when an immutable root entity lacks its ID.
- `upsert` is PostgreSQL native and matches on the primary key; it **does not** delete existing children omitted from the supplied collection. With `@Version`, the conflict branch rejects stale versions.
- Cascade deletion resolves persisted children by foreign key even if the in-memory collection is empty, and deletes descendants before their parents. Use DB foreign keys for referential integrity. This is **not** JPA orphan removal or lazy loading.
- Every graph is committed or rolled back atomically. Cyclic/repeated in-memory entities are rejected; this API has no identity map, no transparent retry, and no global context.

## Spring MVC Pageable, Page and Slice

The optional `bjorm-spring-boot` module depends on `spring-data-commons` **only for paging types**;
the JDBC-only core remains independent of Spring Data and JPA.

Enable Spring Data web argument resolution in your MVC application with `@EnableSpringDataWebSupport`:

```java
@GetMapping("/products/page")
Page<Product> page(@PageableDefault(sort = "name") Pageable pageable) {
    return bjormPages.page(Product.class, Product_.active.eq(true), pageable);
}

@GetMapping("/products/slice")
Slice<Product> slice(@PageableDefault(sort = "name") Pageable pageable) {
    return bjormPages.slice(Product.class, Product_.active.eq(true), pageable);
}
```

`Page` executes a paginated `SELECT` plus `SELECT COUNT(*)` with the same predicate;
`Slice` performs **one** `SELECT` using `LIMIT pageSize + 1` and `OFFSET`, then removes
the lookahead row and sets `hasNext`. Neither creates an intermediate row map.
A `sort` property is translated using the compile-time-generated entity mapper; unknown fields are rejected.
`ignoreCase` and non-native null-ordering requests are rejected rather than silently ignored.
`Pageable.getOffset()` is bound as a JDBC `long` to support high offsets, although
large-offset pagination may be slower than keyset pagination.
For stable ordering include a unique tie-break field (such as `sort=name,asc&sort=id,asc`).
For stable JSON APIs prefer `PagedModel` rather than directly serializing Spring Data `PageImpl`.

The `Page` count and row query are separate JDBC operations; use an appropriate database
transaction isolation level if a consistent snapshot across concurrent writes is required.


### JDBC driver-level row budgets (0.3.10)

Materialized SELECTs now also call `PreparedStatement.setMaxRows(maxBufferedRows + 1)` and reject the lookahead row. This prevents pgJDBC from fetching an unbounded materialized result before Java-side row checks. `forEach`/`scan` remain cursor-based, without a global row cutoff, but eager graph hydration bounds a single root's accumulated children. Query timeout is a driver cancellation request, not a hard deadline; consider database `statement_timeout`, `lock_timeout` and socket timeouts in production.

## Configurable @Children and QueryDSL-style JOIN (0.3.11)

`@Children(mappedBy="orderId", type=JoinType.LEFT)` uses LEFT JOIN, preserving
parents without children. Other supported types are `INNER`, `RIGHT` and `FULL`;
the default is `LEFT` to preserve previous entity-loading semantics.

```java
var orders = db.select(ItOrder.class).as("o")
    .leftJoin(ItOrderLine.class,"l")
    .on(ItOrder_.id.as("o").sameAs(ItOrderLine_.orderId.as("l")))
    .fetch();
```

The DSL also supports `join`/`innerJoin`, `rightJoin`, `fullJoin`/`outerJoin`
and `fullOuterJoin`, both directly and with `.on(...)`; `FieldSelect`
projections support the same join types. ON expressions use mapped typed fields.

Explicit JOINs and automatically generated child joins execute as one SQL
statement; root rows from explicit joins are deduplicated before expanding
collections. Sibling child collections use independent `UNION ALL` branches,
not a cartesian product. `LIMIT/OFFSET` over an explicit join is rejected.
RIGHT/FULL can produce unmatched child rows; rooted entity results skip them,
while SQL projections can expose the outer-join result directly.

## SQL dialects (0.3.12)

Mapped CRUD/joins now choose a dialect at BJORM construction. No JDBC driver is
bundled in the core; the consumer supplies the correct JDBC driver and DataSource.

```java
Bjorm db = Bjorm.open(source, BjormOptions.defaults(), SqlDialects.MYSQL,
                      Product_BjormMapper.INSTANCE);
```

In Spring Boot 4: set `bjorm.dialect: mysql` (default: `postgresql`).

- **PostgreSQL 17**: UUID native, JSONB, ON CONFLICT upsert and all join types.
- **MySQL 8.4**: store UUIDs in `CHAR(36)`, native JSON and
  `ON DUPLICATE KEY UPDATE` with row aliases.
- `FULL OUTER JOIN` is not available on MySQL; explicitly rejected.
- MySQL upsert on `@Version` is refused; using MySQL UPSERT with multiple
  UNIQUE indexes can update a different conflict target than PostgreSQL.
- `@Query` native SQL and manually supplied SQL may be database-specific;
  the dialect does not automatically rewrite arbitrary SQL.
- Extra dialect implementations can implement `SqlDialect`; verify the
  target JDBC driver's binding and SQL semantics using integration tests.

## Spring Boot integration

The optional `bjorm-spring-boot` adapter targets **Spring Boot 4.1.1** (stable) and uses Spring's JDBC transaction-aware DataSource so `@Transactional` works with the same connection. No Spring dependencies are added to `bjorm-core`. Integration is exercised in `bjorm-demo` against PostgreSQL, including commit and rollback. Configure `bjorm.dialect` to select the SQL dialect.
