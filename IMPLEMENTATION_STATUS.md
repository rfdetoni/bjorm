# BJORM implementation status — 0.3.6-SNAPSHOT

This is a new codebase derived from the *requirements* and concepts of WORM. It is not a copy of WORM and intentionally has no backward compatibility.

## Completed in local source

- Direct JDBC, dependency-free `bjorm-core`; generated code avoids reflection/dynamic proxies in hot paths
- Immutable `Bjorm` mapper registry; explicit and ServiceLoader startup discovery
- Typed DSL: comparisons, NULL, IN, BETWEEN, AND/OR/NOT, aliases, joins, ordering, limit/offset
- Compile-time record and mutable POJO CRUD mappers, typed fields, safe SQL identifiers
- Compile-time native query repositories via `@Query`, positional JDBC binding from `@Param`, SQL lexical handling of literals/comments/PG casts
- DTO record projections (`@Projection`) and scalar static SQL query results
- `findOne`/`first` via `LIMIT 1`, partial selects by mapped Java property name, direct scalar `findColumn`/`findColumnOne` without row Map allocation
- Active Record conveniences with context parameter and no static runtime locator
- Explicit transactions; batch insert/update; fetch-size bounded scans and `scan` for custom projections
- PostgreSQL JSONB string bindings, optimistic locking, POJO generated keys
- Separate Spring Boot 4 module; JMH mapper-vs-manual source benchmark; GitHub CI workflow

## Validated with available local environment

- javac 21 (available in container): core, processor, examples, all generated Java and smoke tests
- Critical smoke: SQL/binding order, Active Record, POJO/record mapping, tx ownership/rollback, aliases/joins, `findOne`/`findFields`/scalar selections, generated repository/projection, version check, batch and generated key behavior (JDBC test double)
- Negative compile gate: invalid table identifier and unknown query parameter rejected
- Source scan: no Spring imports in core/processor

## NOT verified / remaining work

- JDK 25 Maven full reactor compile (JDK25 + Maven unavailable here)
- Spring Boot 4 integration test and real `@Transactional` behavior (needs dependencies)
- Actual PostgreSQL integration execution: opt-in integration source exists, but no DB/driver configured here
- Benchmarks with real PostgreSQL and equivalent handwritten JDBC baseline; no measured speedups or memory data
- MySQL-specific SQL behavior and dialect matrix; not covered until PostgreSQL parity is validated
- Additional nonessential features (implicit graph cascade (without @Children), lazy load, session, identity map, L2 cache) explicitly out of scope

## Publishing

`rfdetoni/bjorm` is the intended origin and `main` the publication branch. Maven GitHub Packages publication requires successfully executing the GitHub Actions workflow; this document does not assert release success. Do not call this production ready.


## Release automation (0.3.6-SNAPSHOT)

- Configured GitHub Packages Maven `distributionManagement` for the reactor.
- `main` snapshot deployments and workflow-dispatched stable release with version bump are added but **not executed on GitHub**. They require source pushed to `main` and successful CI credentials/build.
- GitHub Actions/JDK25/Maven integration has not been verified in this environment.

## Namespace migration (0.3.6-SNAPSHOT)

- Public API and generated code package: `com.github.rfdetoni.bjorm` (previously `dev.bjorm`).
- Maven groupId for all modules: `com.github.rfdetoni.bjorm`; artifacts and GitHub Packages URL unchanged.
- No compatibility shims, since this project is pre-production.

## 0.3.6-SNAPSHOT

UUID v7, native upsert and explicit recursive `@Children` graph persistence/deletion implemented. Critical smoke and PostgreSQL integration tests prepared. PostgreSQL integration must pass on CI; no claim about performance improvement without benchmark evidence.

- Automatic @Id inference and immutable record-return APIs implemented; Java 21 `scripts/verify.sh` covers generated SQL, binding, batch and record graph logic. Java 25 + PostgreSQL CI remains authoritative for real database identities and transaction behavior.

- 0.3.10-SNAPSHOT: JDBC materialized row budgets applied on PreparedStatement as well as Java-side loops; unbounded cursor streaming remains possible via forEach/scan.

## Join types (0.3.11-SNAPSHOT)

- Compile @Children(type=LEFT/INNER/RIGHT/FULL) into generated child metadata (default LEFT).
- QueryDSL-style join(...).on(...) with LEFT, RIGHT, INNER, FULL OUTER, including projections.
- Explicit filtering joins and eager graph expansion share one SQL with root deduplication.
- Pagination that can multiply roots is blocked; RIGHT/FULL unmatched child-only rows are not parent entities.
- Java smoke/SQL-shape and opt-in PostgreSQL coverage added.

- Spring Boot adapter BOM upgraded to 4.1.1 in 0.3.14-SNAPSHOT; the consumer's real PostgreSQL `@Transactional` test validates integration and cannot be inferred from a Java-only smoke run.

## 0.3.15-SNAPSHOT

- Implemented compile-time mapped superclass field inheritance, with collision rejection and a PostgreSQL integration fixture.
- Implemented typed JSON codecs for entity fields (including generic List<T>); generated JDBC binding and row mapping support String/default and typed/custom codecs.
- Still application-specific: audit author/time, soft-delete filters, tenant/multi-DataSource routing, singular read-only relation mapping and LJF/LiviaPatient migrations. Never claim these are done by the BJORM core.
