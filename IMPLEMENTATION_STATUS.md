# BJORM implementation status — 0.3.4-SNAPSHOT

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
- Additional nonessential features (auto graph cascade, lazy load, session, identity map, L2 cache) explicitly out of scope

## Publishing

`rfdetoni/bjorm` is the origin and `main` is the publication branch. Maven GitHub Packages publishing requires successful GitHub Actions execution; this document does not assert release success.


## Release automation (0.3.4-SNAPSHOT)

- Configured GitHub Packages Maven `distributionManagement` for the reactor.
- `main` snapshot deployments and workflow-dispatched stable release with version bump are added but **not executed on GitHub**. They require valid source on `main`, a successful JDK 25 build and permissions.
- GitHub Actions/JDK25/Maven integration has not been verified in this environment.

## Namespace migration (0.3.4-SNAPSHOT)

- Maven groupId, Java packages, generated classes, ServiceLoader and Spring adapter use `com.github.rfdetoni.bjorm`.
- `javax.sql` belongs to Java SE `java.sql` and `javax.annotation.processing` belongs to Java SE `java.compiler`; these do not require Jakarta EE.
