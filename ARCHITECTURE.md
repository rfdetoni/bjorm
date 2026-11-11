# BJORM — architecture, implementation and acceptance status

## Product

Bare Metal Java ORM. Java 25, PostgreSQL-first, direct JDBC, compile-time generated mappings, no mandatory frameworks, no WORM API compatibility. `0.3.19-SNAPSHOT` is an experimental codebase, **not production-ready**.

## Modules

| Module | Responsibility |
|---|---|
| `bjorm-core` | Core APIs, JDBC execution, query DSL, explicit transactions, small helpers, zero runtime dependencies |
| `bjorm-processor` | `javac` annotation processor for POJO/record CRUD, typed fields, `@Projection`, `@Query` |
| `bjorm-examples` | Source examples, processor/smoke checks and PostgreSQL/MySQL real-driver integrations |
| `bjorm-spring-boot` | Optional Boot 4 auto configuration; never imported by core |
| `bjorm-benchmarks` | JMH benchmark (mapper vs manual reader; real database benchmarking still needed) |

## Dependencies and state

- Core uses only JDK/JDBC; `javax.sql.DataSource` is part of Java SE 25 (`java.sql` module), not legacy Java EE/Jakarta EE. The consuming application supplies the `DataSource`, JDBC driver, and optional connection pool.
- Instance-owned immutable entity registry (`Bjorm`); no static locator or global mutable cache. Generated mapper instances hold no connection. Startup-only ServiceLoader discovery is optional; explicit mapper registration is also supported.
- All values flow to `PreparedStatement` binders; SQL identifiers are validated at compile-time or through generated `Field`/`SqlOrder` types.
- `@Query` literals compiled by javac into positional binding; SQL body can still be invalid for a live database (schema and vendor syntax not checked).
- Explicit `Bjorm.tx`: single owned connection; `Transaction` guarded against reuse or cross-thread access. Calling `db` methods inside a tx instead of `tx` would borrow another connection and is prohibited by contract.
- Batch is transactional by default when called from `db`; batches inside `tx` reuse its connection; driver `SUCCESS_NO_INFO` is rejected for optimistic versioned batch.
- `forEach` invokes consumers within a read transaction with cursor-friendly fetch size. The processor never exposes `ResultSet` beyond a controlled scope.
- Spring Boot adapter wraps the target DataSource in `TransactionAwareDataSourceProxy` so Spring-managed transaction context is respected; explicit `Bjorm.tx` cannot nest into an existing managed JDBC transaction; internal multi-statement operations join that transaction instead.

## Transaction-bound row locks

`lockOne` and `lockRows(type, predicate, mode, limit)` refuse auto-commit
connections before applying tenant scope. This avoids creating and immediately
committing a short-lived PostgreSQL `SET LOCAL` transaction that would release
locks before the caller can work. `lockRows` is deliberately restricted to flat
single-key entities, requires a predicate and a positive bounded limit, and
orders by the mapped primary key. `SKIP LOCKED` is suitable for competing queue
consumers, not a consistent snapshot.

## Capability matrix

| From WORM / planned | Status | Notes |
|---|---|---|
| Active Record optional | Implemented | `insert(db)`, `update(db)`, `delete(db)`; context explicit |
| Typed DSL | Implemented (subset) | AND, OR, NOT, IN, BETWEEN, IS NULL, comparisons, ordering, limit/offset |
| Static SQL | Implemented | Generated CRUD query strings and `@Query` binders with named parameters |
| Compiled row mapping | Implemented | Records, POJOs, scalar return, projection DTO records |
| JDBC transactions | Implemented | Commit/rollback, one connection per explicit tx |
| Optimistic locking | Implemented | Versioned update/delete/upsert CAS; `updateReturning` returns current DB state |
| Batch write | Implemented | Insert/update, bounded batch size 256, typed binders |
| Generated keys | Implemented | POJO and immutable record identity returns; numeric batch uses individual inserts |
| Streaming reads | Implemented | `forEach`, `scan` and transaction-bound `withStream` with controlled resource closure |
| Joins and projections | Implemented | Explicit typed joins, named-property selection (maps or compiled DTOs), scalars, native `@Query` readers |
| JSON | PostgreSQL JSONB / MySQL JSON | String and typed `@Json` fields via user-provided codecs |
| Tenancy | Tenant-required mode | Tenant-required schema/catalog routing, native-SQL denial, and real restricted-credential isolation checks; application still owns IAM and privileged pools |
| Spring Boot | Boot 4.1.1 adapter | Transaction-aware DataSource with PostgreSQL/MySQL real-driver Spring commit/rollback integration tests; verify each consuming application's authorization/pool boundaries |
| `findOne` / named-property `find` / scalar | Implemented | `Optional<T>` with `LIMIT 1`, property-keyed partial rows, typed scalar and `@Projection` DTO, no partial entities |
| SQL shape cache | Deliberately omitted | Need measurable gain before adding storage/caching complexity |
| MySQL | Supported subset | MySQL 8.4 CRUD/JSON/UUID/JOIN and atomic versioned UPSERT with CAS; FULL OUTER JOIN unsupported |
| Performance benchmarks | JMH source present | Correctness/compilation verified; controlled throughput/allocation and tail latency pending |
| Real PostgreSQL tests | CI integrated | Disposable PostgreSQL 17 service plus Java 25 verification in `build.yml` |
| Transparent lazy-loading/L2 cache | Not implemented | Any future opt-in needs an explicitly scoped lifetime and measured benefit |

## Open engineering gates before production

1. Require Java 25, PostgreSQL 17 and MySQL 8.4 GitHub CI checks to pass on each revision.
2. Verify tenant-specific IAM/pool credential boundaries and external consumers; Spring transaction join is tested with real PostgreSQL/MySQL drivers.
3. Extend database coverage for concurrent updates, lock contention and JDBC driver compatibility.
4. Run reproducible JDBC/JMH benchmarks with equivalent SQL and transaction boundaries; report allocations and tail latency.
5. Deliver explicitly opt-in soft-delete/filters and missing relation features with compile-time and database acceptance tests; never silently change legacy delete behavior.

## Performance standard

No unsupported throughput claims. Compare equivalent SQL, statement caching, connection pool settings, transaction boundaries, warmed JVM, row counts and concurrency. Remove abstractions only when they reduce overhead or improve correctness/maintainability.

## Delivery

GitHub repository: `rfdetoni/bjorm`. GitHub Actions runs Java 25, disposable PostgreSQL 17 and MySQL 8.4 checks; only the `build.yml` gated snapshot job publishes snapshots. Check each run for the current commit.

## Explicit graph persistence (0.3.6)

- Generated `@Id(uuidV7=true)` for mutable POJO UUID fields and `UuidV7.next()` for immutable record construction.
- PostgreSQL primary-key `ON CONFLICT DO UPDATE` generated at compile time, with `@Version` predicate on conflict.
- Generated `@Children(mappedBy="foreignKeyJavaProperty")` metadata. Parent inserts and upserts recurse in dependency order, assigning child FKs after parent ID assignment. Deletion looks up persisted descendants by FK and removes them before the parent. Operations are atomic in one JDBC transaction.
- No orphan reconciliation, implicit global session or reflection-based relationship discovery; implicit cascades without `@Children` remain out of scope.

## Automatic identifier generation (0.3.8)

The annotation processor determines `@Id` behavior from declared Java types without runtime reflection: UUID and String receive UUID v7 if null; int/long and boxed variants require an IDENTITY column in the database and use generated keys. `@Id(assigned=true)` disables inference. A Java record is immutable and must be persisted with `insertReturning` when its ID is absent; the mapper creates a new record with its assigned ID. Cascades rebuild record child foreign keys and return the persisted graph.

## Typed domain compatibility (0.3.15)

Inherited POJO columns and @Id are compiled by the annotation processor from the superclass chain (base-first, no per-row reflection). Typed JSON uses application-provided JsonCodec<T> instances stored in the generated mapper; the core has no Jackson dependency. Domain-specific authorization/provisioning, soft-delete semantics and module-aware routing still need explicit contracts and tenant-specific integration tests.
