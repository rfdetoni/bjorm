# BJORM — architecture, implementation and acceptance status

## Product

Bare Metal Java ORM. Java 25, PostgreSQL-first, direct JDBC, compile-time generated mappings, no mandatory frameworks, no WORM API compatibility. `0.3.1-SNAPSHOT` is an experimental codebase, **not production-ready**.

## Modules

| Module | Responsibility |
|---|---|
| `bjorm-core` | Core APIs, JDBC execution, query DSL, explicit transactions, small helpers, zero runtime dependencies |
| `bjorm-processor` | `javac` annotation processor for POJO/record CRUD, typed fields, `@Projection`, `@Query` |
| `bjorm-examples` | Small source examples and smoke + opt-in PostgreSQL integration checks |
| `bjorm-spring-boot` | Optional Boot 4 auto configuration; never imported by core |
| `bjorm-benchmarks` | JMH benchmark (mapper vs manual reader; real database benchmarking still needed) |

## Dependencies and state

- Core uses only JDK/JDBC; `javax.sql.DataSource` is provided by the consuming application, with optional pool/driver.
- Instance-owned immutable entity registry (`Bjorm`); no static locator or global mutable cache. Generated mapper instances hold no connection. Startup-only ServiceLoader discovery is optional; explicit mapper registration is also supported.
- All values flow to `PreparedStatement` binders; SQL identifiers are validated at compile-time or through generated `Field`/`SqlOrder` types.
- `@Query` literals compiled by javac into positional binding; SQL body can still be invalid for a live database (schema and vendor syntax not checked).
- Explicit `Bjorm.tx`: single owned connection; `Transaction` guarded against reuse or cross-thread access. Calling `db` methods inside a tx instead of `tx` would borrow another connection and is prohibited by contract.
- Batch is transactional by default when called from `db`; batches inside `tx` reuse its connection; driver `SUCCESS_NO_INFO` is rejected for optimistic versioned batch.
- `forEach` invokes consumers within a read transaction with cursor-friendly fetch size. The processor never exposes `ResultSet` beyond a controlled scope.
- Spring Boot adapter wraps the target DataSource in `TransactionAwareDataSourceProxy` so Spring-managed transaction context is respected; normal `Bjorm.tx` cannot nest into an existing managed JDBC transaction.

## Capability matrix

| From WORM / planned | Status | Notes |
|---|---|---|
| Active Record optional | Implemented | `insert(db)`, `update(db)`, `delete(db)`; context explicit |
| Typed DSL | Implemented (subset) | AND, OR, NOT, IN, BETWEEN, IS NULL, comparisons, ordering, limit/offset |
| Static SQL | Implemented | Generated CRUD query strings and `@Query` binders with named parameters |
| Compiled row mapping | Implemented | Records, POJOs, scalar return, projection DTO records |
| JDBC transactions | Implemented | Commit/rollback, one connection per explicit tx |
| Optimistic locking | Implemented | Versioned update/delete; caller refreshes after update |
| Batch write | Implemented | Insert/update, bounded batch size 256, typed binders |
| Generated keys | Implemented (subset) | POJO only; batch IDs not implemented |
| Streaming reads | Implemented | `forEach` typed entity scan and `scan` with custom/projection readers |
| Joins and projections | Implemented | Explicit typed joins, named-property selection (maps or compiled DTOs), scalars, native `@Query` readers |
| JSON | PostgreSQL `jsonb` string | `@Json String`; custom serializers remain application-owned |
| Tenancy | Through DataSource | Application provides tenant-aware DataSource; no global tenant state |
| Spring Boot | Adapter source present | Actual Spring Boot 4 build/context integration **not verified** |
| `findOne` / named-property `find` / scalar | Implemented | `Optional<T>` with `LIMIT 1`, property-keyed partial rows, typed scalar and `@Projection` DTO, no partial entities |
| SQL shape cache | Deliberately omitted | Need measurable gain before adding storage/caching complexity |
| MySQL | Not yet | Requires dedicated dialect + real integration tests |
| Performance benchmarks | JMH source present | Requires JDK25/Maven and real JDBC comparison |
| Real PostgreSQL tests | Available opt-in | Not executed without test database/driver |
| Transparent lazy-loading/L2 cache | Out of scope | Would violate target simplicity |

## Open engineering gates before production

1. Compile full Maven reactor with JDK 25, including optional Spring/JMH dependencies, then run Java 25 CI gates.
2. Run PostgreSQL integration in an **isolated disposable DB**, with CRUD/null/JSON/version/batch/generation concurrency and actual transaction semantics.
3. Run actual Spring Boot 4.0.x application integration to verify injection and `@Transactional` enlistment.
4. Add JDBC driver matrix and typed compatibility checks for date/time, nullable JSON, generated key behavior.
5. Validate the new selected-property queries against real PostgreSQL and add keyset helpers only if motivated by concrete use cases; avoid generic AST/proxy frameworks.
6. Run reproducible JMH and full-database benchmarks versus equivalent manual JDBC + WORM; report alloc/op, CPU, throughput, latency p50/p95/p99.
7. Publish artifacts and repo only after build/CI gates; document license and package ownership.

## Performance standard

No unsupported throughput claims. Compare equivalent SQL, statement caching, connection pool settings, transaction boundaries, warmed JVM, row counts and concurrency. Remove abstractions only when they reduce overhead or improve correctness/maintainability.

## Delivery

No GitHub repository was created or changed by this task; this is a local source deliverable. Once `rfdetoni/bjorm` exists, the source can be imported and validated in Actions.
