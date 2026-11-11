# Changelog

## 0.3.19-SNAPSHOT (development, not a stable release)

- Add generated detached POJO snapshots, independently copying supported scalar `@Embedded` records. Reject mutable graph/typed JSON/converted shapes when deep-copy safety cannot be guaranteed; retain legacy mutable INSERT/UPSERT compatibility (#30, #35, #38).
- Enforce database privileges with real restricted per-tenant LOGIN accounts in PostgreSQL and MySQL 8.4 integration tests; native SQL opt-in does not grant cross-tenant rights (#30, #34).
- Support direct root/child to-one reads with one eager graph statement for supported shapes, and explicit assigned composite IDs on independent entities; reject unsupported composite FK and referenced deep graph associations (#31, #27).
- Extend lock modes with UPDATE/SHARE NOWAIT/SKIP LOCKED (#33). Require caller-owned transaction for row locking in tenant-required contexts; add bounded primary-key-ordered `lockRows` for flat entities. The new `Operations.lockRows` is a default method that fails explicitly on custom legacy implementations, preserving source compatibility; `Bjorm` and its transaction implement the operation.
- Add opt-in isolation modes `READ_COMMITTED`, `REPEATABLE_READ`, and `SERIALIZABLE` to explicitly owned transactions; no implicit retries or Spring-bound isolation overrides (#36).
- Add compile-time `@Converted` JDBC value converters and typed-field predicate conversion without runtime entity reflection (#37).
- Introduce JMH source suites for UUID, JDBC mapping and PostgreSQL round-trips; no controlled JMH performance conclusions have been published (#32).
- Align architecture/status documents to actual 0.3.19 capabilities; keep remaining roadmap open in issue #11.


## 0.3.18-SNAPSHOT

- Expose `Operations.params(Object...)` for reusable, dialect-aware native SQL parameter binding without repeated application-specific `StatementBinder` loops. Snapshot values before execution to avoid mutable-varargs races; available within transactions.
- Add JDBC-spy regression for captured values, UUIDs, Instant timestamps and transaction usage, plus PostgreSQL/MySQL native-parameter integration coverage.

- Fix compile-time generated entity binders and readers for `java.time.Instant` on PostgreSQL/MySQL, handling null values and avoiding unsupported JDBC `setObject(Instant)` and `getObject(Instant.class)`.
- Add cross-dialect integration coverage for record insertion, versioned update, nullable timestamp and roundtrip.

- Bind `java.time.Instant` as JDBC `Timestamp` in native BJORM `SqlDialect.bindValue`, avoiding PostgreSQL driver failures in custom SQL queries and automation writes.
- Add real PostgreSQL/MySQL native timestamp parameter roundtrip regression tests.

- Expose native typed `COUNT(*)` consistently on both database and transaction `Operations`.
- Add `exists()` as `SELECT 1 ... LIMIT 1`, binding predicate parameters through the active SQL dialect without entity instantiation.
- Add transaction-only `lockOne()` with `LockMode.UPDATE/SHARE`, explicit auto-commit guard and no implicit transaction.
- Add JDBC-spy and PostgreSQL integration regressions for aggregates, row locks and transaction connection reuse.

- Add framework-neutral validated Pageable and Slice<T> with safe sort-property grammar, bounded offset policy and count-free lookahead construction.
- Add pagination smoke regression and align reactor version pins.


## 0.3.17-SNAPSHOT

- Fix eager graph hydration when the same generated child relation is reachable through different parent paths; identify relation slots by parent context.
- Reject graph sorting on non-root columns with an explicit error before SQL execution.
- Bind native `@Query` UUID arguments and decode scalar UUID results according to the current JDBC dialect (including MySQL CHAR(36)).
- Resolve inherited child foreign-key fields when compiling `@Children(mappedBy=...)`.
- Guard MySQL native UPSERT so a secondary UNIQUE collision cannot silently update another UUID/String-PK row. Explicitly refuse unsafe numeric-key MySQL UPSERT.
- Add focused graph/compiled-mapping/MySQL integration regressions for these issues.
- Consolidate snapshot publishing in the Java/PostgreSQL/MySQL-gated `build.yml`; do not publish stable-version commits as snapshots.
- Align all module versions and current capability/acceptance documentation.


## 0.3.15-SNAPSHOT

- Compile persistent POJO fields declared in superclasses, including inherited @Id, @Column, @Version and @Children; access them through public inherited getters/setters, with deterministic base-first column ordering and shadowing rejection. No reflection in CRUD.
- Extend @Json to typed objects/collections via explicit compile-time JsonCodec<T> classes, keeping existing String fields unchanged and JSON parameter casts selected by SQL dialect.
- Reuse stateless per-mapper codec instances; preserve SQL NULL values, generate typed readers/binders and fail compilation when a typed @Json field lacks a codec.
- Add real PostgreSQL integration checks for inherited ID/createdAt fields and typed JSON list round-trips, with regression smoke checks for both PostgreSQL and MySQL dialect casts.
- Keep soft-delete/audit/tenant routing on their explicit domain boundaries instead of implicitly changing delete semantics or adding global context.

## 0.3.14-SNAPSHOT

- Upgrade optional `bjorm-spring-boot` dependency management from Spring Boot 4.0.3 to latest stable 4.1.1; preserve Spring-free core and automatic DataSource/transaction integration.
- Align the complete Maven reactor to 0.3.14-SNAPSHOT and validate against a Spring Boot 4.1.1 demo with `@Transactional` commit/rollback and PostgreSQL HTTP smoke tests.

## 0.3.13-SNAPSHOT

- Fix MySQL typed DSL predicate binding: use SqlDialect.bindValue for WHERE parameters, not raw PreparedStatement.setObject(UUID).
- Guard the MySQL CHAR(36) UUID predicate in JDBC-spy tests; require the real MySQL 8.4 CI job to pass before claiming dialect parity.


## 0.3.12-SNAPSHOT

- Introduce a minimal `SqlDialect` SPI with PostgreSQL and MySQL 8.4 implementations, without driver dependencies in the core.
- Delegate native upsert, JSON parameters, UUID binding and supported JOIN operations to the selected dialect.
- Add Spring property `bjorm.dialect` (postgresql/mysql), retaining PostgreSQL as default.
- MySQL uses a row alias instead of deprecated VALUES(col) in ON DUPLICATE KEY UPDATE.
- Refuse MySQL FULL OUTER JOIN and versioned UPSERT until semantics are safely supported.
- Add MySQL 8.4 CI and real JDBC integration plus dialect unit smoke tests.


## 0.3.11-SNAPSHOT

- Generate per-relation join type for @Children: LEFT(default), INNER, RIGHT and FULL OUTER.
- Add QueryDSL-style .join(...).on(...), leftJoin, rightJoin, fullJoin/outerJoin on entity and field projections.
- Permit non-paginated explicit joins with eager children in one SQL, deduplicating roots before UNION ALL child branches.
- Keep unsafe JOIN pagination rejected; ignore unmatched child-only rows when reading root entities.
- Add regression and PostgreSQL integration tests for join SQL semantics.


## 0.3.10-SNAPSHOT

- Enforce JDBC setMaxRows(maxBufferedRows + 1) for materialized SELECTs, avoiding unbounded driver-side buffering before application limits are checked.
- Preserve streaming behavior for forEach/scan and enforce row budgets on automatically joined entity graphs.

## 0.3.9-SNAPSHOT

- Automatically hydrate mapped @Children with a single parent-first SELECT and LEFT JOIN; multiple siblings use UNION ALL to prevent Cartesian multiplication.
- Add generated offset row readers, typed ID column metadata, nested record graph reconstruction and per-parent deduplication.
- Introduce JDBC query timeout, row budgets, configurable cursor fetch size and version-aware cascade deletes.


## 0.3.8-SNAPSHOT

- Infer automatic `@Id` strategies from Java type: UUID v7 for UUID/String and JDBC identity for int/long and wrappers. Use `assigned=true` to opt out.
- Add `insertReturning`, `upsertReturning` and `batchInsertReturning` to return immutable records with generated IDs; POJO IDs are assigned in-place.
- Generate record constructors and immutable child foreign-key wiring at compile time, including nested parent/child graphs.
- Add POJO, record, batch, identity and immutable graph regression checks, plus disposable PostgreSQL integration coverage.

## 0.3.7-SNAPSHOT

- Add optional Spring Data `Pageable`, `Page<T>` and count-free `Slice<T>` integration via `BjormPages`.
- Add generated-metadata-backed sort validation and typed `COUNT(*)` predicate binding.
- Support JDBC long offsets and page size + 1 lookahead; preserve Spring-free JDBC core.


## 0.3.6-SNAPSHOT

- Native RFC 9562 UUID v7 generation through `UuidV7.next()` and compile-time `@Id(uuidV7=true)` on mutable entity IDs.
- PostgreSQL `INSERT ... ON CONFLICT DO UPDATE` generated per entity, including version-aware conflict detection.
- `@Children(mappedBy="...")` generates FK wiring for recursive parent-first inserts/upserts and database-backed child-first deletion in one JDBC transaction.
- Validate parent/child foreign-key integrity, rollback on failure, null children and cycle detection without reflection or global mutable state.
- Add a PostgreSQL integration scenario covering UUID v7, upsert, cascade deletion of unloaded children and atomic rollback.

## 0.3.5-SNAPSHOT

- Preserve direct `setBigDecimal` mapping and critical JDBC spy smoke checks.
- Install generated processor/core artifacts before Maven reactor verification in Java 25 build and release workflows.

## 0.3.4-SNAPSHOT

- Generate direct `PreparedStatement.setBigDecimal` instead of generic `setObject` for BigDecimal fields, aligning the compiled JDBC binding with handwritten JDBC.
- Treat transaction benchmark parity as a separate demonstration concern; no caches or extra runtime layers introduced without evidence.
- Fix the JMH ResultSet stub (`wasNull`) and give the handwritten mapping the same non-null primitive checks as generated code.

## 0.3.3-SNAPSHOT

- Move Java API, processor, generated symbols, ServiceLoader registration and Spring adapter from `dev.bjorm` to `com.github.rfdetoni.bjorm`.
- Align Maven parent, module dependencies and annotation processor paths with groupId `com.github.rfdetoni.bjorm`.
- Update processor registration, Spring auto-configuration metadata, verification scripts, consumer documentation and the release version to `0.3.3-SNAPSHOT`; no backwards compatibility shim.
- Document why JDK JDBC and annotation processing still use `javax.sql` and `javax.annotation.processing`.
- Keep GitHub Packages snapshot and stable release workflows, with Maven reactor version checks.

## 0.3.1-SNAPSHOT (unreleased)

- Added `findOne` and DSL `first()` with `Optional<T>` and SQL `LIMIT 1`, including transactional use.
- Added named Java-property selection with `findFields`, `findOneFields`, and `select(...).fields(...).fetch/first`.
- Generated per-entity property-to-SQL-column resolution and JDBC typed readers at compile time; supports `@Column` renaming, nullable values and enums.
- Added zero-intermediate-map scalar `findColumn`, `findColumnOne`, `values` and `firstValue`.
- Added direct DTO `@Projection` readers for selected fields, and regression smoke tests (SQL, bind order, missing rows, validation, transaction scoping).
- PostgreSQL integration assertions expanded (not executed locally; requires a configured disposable database).

## 0.3.0-SNAPSHOT

- First separate BJORM development line; no source compatibility with WORM.
- JDBC core and transaction-scoped connection management; no Spring dependency in the core.
- Compile-time POJO and record mapping, optional Active Record, typed DSL.
- Generate `@Query` implementations with named parameters and positional SQL bindings.
- `@Projection` compiled record readers, typed DSL joins, `@Version` locking, batch, generated IDs for POJOs.
- ServiceLoader registration emitted by annotation processor to allow `Bjorm.open(dataSource)`.
- PostgreSQL `jsonb` string support, incremental entity reads and optional Spring Boot 4 adapter.
- Essential smoke tests and opt-in PostgreSQL integration source; preliminary JMH mapping source.
- Maven JDK25 CI draft; external module builds and real DB benchmarks still unverified.
