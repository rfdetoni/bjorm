# BJORM — Bare Metal Java ORM

**Experimental 0.3.19-SNAPSHOT** — a minimal, compile-time-assisted relational mapper for Java 25. Zero Spring/JPA/Hibernate dependencies in the core; SQL and JDBC remain visible and under application control.

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

## Explicit primary-key generation strategy

Legacy `@Id` inference remains **unchanged** for source and storage compatibility:
`UUID` and `String` produce UUIDv7; numeric `int/long` use database identities.
New records can choose their strategy at compile time:

```java
public record ExternalRecord(@Id(strategy = IdStrategy.ASSIGNED) Long id, String name) {}
public record UuidRecord(@Id(strategy = IdStrategy.UUID_V7) UUID id, String name) {}
public record IdentityRecord(@Id(strategy = IdStrategy.IDENTITY) Long id, String name) {}
```

Do not combine `strategy` with legacy `generated`, `uuidV7`, or `assigned`
flags. The annotation processor checks invalid ID types and contradictory
settings at compilation, rather than generating invalid SQL.

Compiled `@Query` repositories detect PostgreSQL `::` casts only in executable
SQL, ignoring tokens inside comments and string literals. Attempting to execute
such a method with the MySQL dialect now fails **before** preparing SQL.
For cross-dialect queries, use the portable `CAST(expression AS type)` form,
taking into account vendor-specific type names. Native SQL remains dialect
specific beyond this targeted safeguard.

## Native aggregates and row locks (no JdbcClient required)

Use BJORM's generated field predicates for index-friendly COUNT and EXISTS, including
inside `db.tx` and injected Spring `@Transactional` methods:

```java
long total = db.count(Product.class, Product_.name.eq("Pencil"));
boolean any = db.exists(Product.class, Product_.name.eq("Pencil"));
// Result sets are not materialized: COUNT(*) and SELECT 1 ... LIMIT 1.
db.tx(tx -> {
    if (tx.lockOne(Product.class, Product_.id.eq(productId), LockMode.UPDATE)) {
        // Update in the same transaction. Lock is released on commit/rollback.
    }
});
```

`LockMode.UPDATE` and `LockMode.SHARE` map to `FOR UPDATE` and
`FOR SHARE` (PostgreSQL and MySQL 8.4). `lockOne` requires a non-null
typed predicate and a live transaction. A call with an auto-commit connection
fails explicitly; doing row locking outside a transaction would silently
release the lock. A row-missing predicate returns `false`.

The active `SqlDialect.bindValue(...)` handles UUID and `java.time.Instant`
portably (the latter as a JDBC `Timestamp`); native `db.query` / `db.execute`
callers can use it in their `StatementBinder` without custom temporal conversions.

For native SQL, avoid duplicating `StatementBinder` lambdas. `Operations.params(...)`
captures positional arguments once, binds through the selected dialect and works
in the top-level `Bjorm` and in transaction-scoped `Operations`:

```java
long updated = db.execute(
    "UPDATE orders SET checked_at=? WHERE id=?",
    db.params(Instant.now(), orderId));
var result = db.query(
    "SELECT id, title FROM orders WHERE tenant_id=? LIMIT 25",
    db.params(tenantId),
    rs -> new OrderSummary((UUID) rs.getObject(1), rs.getString(2)));
```

All values remain bound parameters; never concatenate user input into SQL.
Use `db.select()`, `@Query` or mapped operations for ordinary queries.

For specialized PostgreSQL SQL (CTEs, aggregates with multiple JOINs, JSONB
expressions and conflict updates by composite key), **use BJORM's existing**
`db.query(sql, binder, mapper)`, `db.one(...)`, `db.execute(...)` or
compile `@Query` repositories before adding Spring JdbcClient. Those are
first-class BJORM APIs, not a reason to expand the DSL with every SQL feature.

## Fixed schema mapping (compile-time, not tenant routing)

A model can target a known PostgreSQL schema or MySQL database with
`@Table(value="jobs", schema="infrastructure")`. Both identifier parts are
validated by the annotation processor; generated SQL uses
`infrastructure.jobs` in `INSERT`, `SELECT`, `UPDATE`, `DELETE` and
dialect-specific UPSERT. No HTTP input or string interpolation is accepted for
the mapped schema name.

**Do not combine fixed and dynamic tenant schemas.** A BJORM created via
`tenantRequired(...)` refuses `forTenant(...)` if *any* registered mapper
declares a static schema. This protects tenant isolation from an explicit
`schema.table` generated reference bypassing PostgreSQL `search_path`.
Use a separate, least-privileged BJORM context for shared infrastructure
tables, and the tenant-required context for tenant-owned tables.

## Tenant-scoped JDBC operations (PostgreSQL and MySQL)

Tenant-aware deployments should build a **tenant-required** root instead of calling `Bjorm.open(...)`:

```java
Bjorm root = Bjorm.tenantRequired(
    dataSource, BjormOptions.defaults(), SqlDialects.POSTGRESQL
);
// Authorize the user's company via your IAM/tenant registry BEFORE mapping it to a schema.
// Never pass an unvalidated HTTP route/query parameter as the tenant schema.
String authorizedSchema = tenantRegistry.authorizedSchemaFor(authenticatedUser);
Bjorm tenantDb = root.forTenant(authorizedSchema);
var customers = tenantDb.select(Customer.class).limit(25).fetch();
tenantDb.tx(tx -> tx.insert(newCustomer));
```

The root rejects JDBC access until scoped. A scoped BJORM instance is immutable and
cannot be rebound to a different tenant. Tenant schema identifiers are strictly
validated; PostgreSQL uses `SET LOCAL search_path` in each transaction, with
`pg_catalog` first, tenant schema next and `pg_temp` last. This avoids
session-level `search_path` state leaking back to the connection pool and
removes `public` as a fallback. MySQL uses `setCatalog` and restores the
original catalog on exit, including failure paths. Unsupported dialects fail
closed. The same rules apply to transaction, batch and scalar APIs.

**Important:** application authorization remains mandatory. Schema selection is
not a substitute for database permissions, RLS or security review of native SQL.
Create least-privilege DB roles, validate tenant-to-schema ownership in the IAM,
never accept arbitrary tenant schema names from clients, and do not run
untrusted native SQL. Prefer per-tenant DB grants when tenants are mutually
untrusted; a connection with access to every schema can still execute
fully-qualified native SQL if the application exposes it.

## Opt-in SQL execution metrics without sensitive data

`Bjorm.observeSql(SqlObserver)` creates an immutable view that tracks the
duration of individual JDBC `executeQuery`, `executeUpdate` and
`executeBatch` calls. When no observer is configured, those JDBC calls
remain direct, without timing or callback allocations.

```java
Bjorm monitored = db.observeSql((operation, durationNanos, succeeded) -> {
    if (durationNanos >= 100_000_000L) {
        metrics.recordSlowQuery(operation.name(), durationNanos, succeeded);
    }
});
```

The observer only receives an enum (`QUERY`, `UPDATE`, `BATCH`),
duration in nanoseconds and a success flag. It **never receives SQL text,
bound values, secrets, tenant schemas or exception messages**. This safe
interface is suitable for metrics cardinality control and slow-call counters.
A listener that throws a normal runtime exception cannot abort or mask the
JDBC call. A listener is synchronous and should not block or perform expensive
work; record a counter or publish to a buffered metrics sink.

Timing ends when JDBC `executeQuery` returns: it **does not include** the
time spent consuming ResultSet rows or rendering entities, and successful
execution does not imply semantic success of each batch row. Monitor full
request latency separately. Dynamic tenant views inherit the observer from
the scoped root; each tenant still uses isolated JDBC connection state.

## Transaction-local identity reuse

BJORM now keeps a **small first-level identity cache only for `tx.find(Type,id)`**
within an explicitly active transaction:

```java
db.tx(tx -> {
    Customer first = tx.find(Customer.class, id);
    Customer again = tx.find(Customer.class, id); // same Java instance, no second SELECT
    tx.update(first);                            // invalidates the per-tx cache
    Customer refreshed = tx.find(Customer.class, id); // fresh SELECT
});
```

The cache is not global, does not cross request threads or tenants, and is
discarded after commit/rollback. It does not intercept `list`, joined graph
queries or direct `Bjorm.find` outside a transaction; therefore it is not
a JPA persistence context or dirty-checking system. The cache is invalidated
by mapped mutations, row-lock acquisition, batch operations and native
`query/one/execute/scan` inside the transaction. If your code mutates the
connection through an API outside BJORM, bypassing these invalidation hooks,
do not rely on the cached instance being fresh.

## Native mapped mutations and richer compile-time mappings

```java
// Writes require a non-null typed predicate. Unknown property names are rejected.
tenantDb.updateBy(Customer.class, Map.of("name", "Updated"), Customer_.id.eq(id));
tenantDb.deleteBy(Customer.class, Customer_.id.eq(id));
tenantDb.batchDelete(List.of(customer1, customer2));
List<Customer> saved = tenantDb.batchUpsertReturning(List.of(customer3, customer4));
Customer current = tenantDb.updateReturning(customer); // reloaded @Version
List<Customer> currentBatch = tenantDb.batchUpdateReturning(List.of(customer1, customer2));

var tuned = new BjormOptions(30, 10_000, 128, 128); // timeout, buffered rows, fetch size, batch size
```

`updateBy/deleteBy` deliberately reject versioned entities and mapped child
graphs because set-based SQL would bypass version checks or child persistence.
Use `updateReturning`, `batchUpdateReturning` and `batchDelete` for those
cases. Versioned MySQL UPSERT remains explicitly unsupported until its
correct compare-and-swap contract is implemented. The update-returning API
re-reads the stored entity within the transaction, so it costs an additional
SELECT, but never fabricates a new version after a failed UPDATE.

Compile-time audit annotations are also supported: `@CreatedAt` initializes a null
`Instant` or UTC `LocalDateTime` on INSERT and is excluded from UPDATE and
UPSERT changes. `@UpdatedAt` initializes a null value on INSERT and stamps
UPDATE, UPSERT and typed `updateBy` automatically. These timestamps are
application-clock based, so synchronize clocks or use database timestamps if
strict cross-node ordering is required. A generated mapper signals when UPSERT
must reload database-generated properties; otherwise record values can be stale.

The annotation processor supports `@Transient` (including record components),
`@Column(value="...", insertable=false, updatable=false)` and opt-in
`@EnumOrdinal` on enum properties. The ordinal is bounds-checked during reads.
Only use ordinal storage when the enum declaration will never be reordered.
Dynamic mapped writes use typed identifiers and dialect-aware JSON bindings;
no JPA, Hibernate or Spring JdbcClient is required.

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
- `db.batchInsert` and `batchUpdate` use up to 256 rows per JDBC batch within one transaction; version-controlled unknown batch counts are rejected instead of assuming successful locking. `batchInsert` **rejects entity types with `@Children`**, instead of silently dropping child rows; use `insertReturning()` for a graph.
- SELECT methods apply the configured `fetchSize` JDBC hint; this is not a hard client-side memory bound. PostgreSQL cursors require `autoCommit=false` to stream incrementally. `db.forEach` and `db.scan(sql, binder, mapper, consumer)` therefore retain explicit transaction boundaries. Resources close before the scan returns; do not keep row cursors outside the callback.
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

The adapter creates the `Bjorm` bean using `TransactionAwareDataSourceProxy`; if no mapper beans are registered, it discovers generated mappers via ServiceLoader. Calls to injected `Bjorm` from a Spring `@Transactional` method participate in Spring-managed JDBC connections. Do **not** nest `db.tx` within Spring-managed transactions; use the Spring transaction boundary or BJORM's explicit transaction, never both.

For schema-per-tenant applications, opt in to **fail-closed** Spring bean wiring:

```yaml
bjorm:
  dialect: postgresql
  tenant:
    required: true
  jdbc:
    batch-size: 128
    query-timeout-seconds: 30
    max-buffered-rows: 100000
    fetch-size: 128
```

With `bjorm.tenant.required=true`, the injected `Bjorm` is a tenant-required
root. Obtain the authorized schema using IAM and call
`bjorm.forTenant(authorizedSchema)` for every request or job. Methods invoked
on the unscoped root fail before acquiring a connection, including during
Spring transactions. `BjormPages` created for the root is also unscoped;
construct tenant-specific paging helpers from the scoped BJORM instance.

A JUnit integration test exercises Spring's `DataSourceTransactionManager` and
`TransactionAwareDataSourceProxy` with a simulated JDBC connection, verifying
connection reuse, rollback propagation, tenant-local PostgreSQL session settings
and rejection of nested `Bjorm.tx`. It does not replace an application-context
test or a real end-to-end Boot service test in your target deployment.

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
- **Snapshot publication** (`build.yml`): the `publish-snapshot` job runs only after Java 25, PostgreSQL 17 and MySQL 8.4 validation succeeds, then deploys `bjorm-core`, `bjorm-processor`, `bjorm-spring-boot` and parent POM to **GitHub Packages**. Stable-version commits skip this snapshot deploy. Examples and benchmarks are not published.
- **Stable release** (`release.yml`): manually dispatch from `main` with a version matching the currently committed snapshot (e.g., `0.3.17` for `0.3.17-SNAPSHOT`). The workflow updates every POM, verifies, commits and tags the stable release, deploys Maven artifacts, then advances `main` to the next development snapshot. All publication uses the workflow's `GITHUB_TOKEN` and no custom credentials are required in the repository.

### Release procedure

A stable release can be requested from an audited `main` commit to
`.github/release-request.txt` containing only the version (for example,
`0.3.18`). The `release-maven` workflow checks the current reactor snapshot
version, validates the release, creates the tag and publishes artifacts, then
advances `main` to the next development snapshot. The normal
`workflow_dispatch` route remains available. Release requests do not trigger
the snapshot build; no release is treated as published until the stable Maven
deploy step finishes successfully.



1. Make sure the latest changes have been pushed to `main` and CI passes.
2. In **GitHub → Actions → release-maven → Run workflow**, select `main` and enter the stable version, e.g. `0.3.17`.
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
    <version>0.3.17-SNAPSHOT</version>
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
parents without children. `INNER` filters out parents without children (including `find(id)`). Other supported types are `INNER`, `RIGHT` and `FULL`;
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
- MySQL upsert with `@Version` is refused. For application-assigned UUID/String
  primary keys, a PK-equality guard rejects a duplicate secondary UNIQUE key
  instead of updating another entity. Numeric primary-key upsert is refused
  because `AUTO_INCREMENT` may generate a key on UPDATE-to-NULL.
- Compiled `@Query` UUID parameters and scalar UUID results use the active
  dialect (PostgreSQL native UUID, MySQL `CHAR(36)`).
- `@Query` native SQL and manually supplied SQL may be database-specific;
  the dialect does not automatically rewrite arbitrary SQL.
- Extra dialect implementations can implement `SqlDialect`; verify the
  target JDBC driver's binding and SQL semantics using integration tests.

## Spring Boot integration

The optional `bjorm-spring-boot` adapter targets **Spring Boot 4.1.1** (stable) and uses Spring's JDBC transaction-aware DataSource so `@Transactional` works with the same connection. No Spring dependencies are added to `bjorm-core`. Integration is exercised in `bjorm-demo` against PostgreSQL, including commit and rollback. Configure `bjorm.dialect` to select the SQL dialect.

## Inherited entity fields (compile-time)

Entity classes can inherit mapped fields from plain Java superclasses. The BJORM processor walks the hierarchy oldest-first, generates access via public getters/setters, and rejects shadowed property names or duplicate mapped columns. Java records retain their own declared components.

```java
public class BaseEntity {
    @Id private UUID id;
    @Column("created_at") private LocalDateTime createdAt;
    // public getters and setters
}
@Table("patients")
public class Patient extends BaseEntity {
    private String name;
    public Patient() {}
    // public getters and setters
}
// db.insert(new Patient()) automatically creates UUID v7 and binds inherited properties.
```

BJORM does **not** mutate created/updated timestamps automatically: lifecycle methods and authenticated-user audit context must be invoked by the domain/service layer. This avoids duplicate or misleading writes during migration.

## Typed JSON (no Jackson dependency in core)

`@Json String` still binds the text directly. For a domain object, list or map, supply a codec whose parameter exactly matches the Java property type:

```java
public final class IntListCodec implements JsonCodec<List<Integer>> {
    public String encode(List<Integer> value) { /* use your JSON serializer */ return "..."; }
    public List<Integer> decode(String json) { /* use your JSON parser */ return List.of(); }
}

@Table("documents")
public record Document(@Id UUID id, @Json(codec = IntListCodec.class) List<Integer> scores) {}
```

In production, implement codecs using your application's Jackson ObjectMapper (or another serializer). The generated mapper keeps one codec instance and calls it on a non-null value; null columns remain SQL NULL. Use thread-safe, stateless codecs because a generated mapper is shared across threads. SQL uses `CAST(? AS jsonb)` on PostgreSQL and `CAST(? AS JSON)` on MySQL. No reflective entity conversion is added to BJORM's hot path.
