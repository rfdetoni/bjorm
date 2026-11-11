# BJORM implementation status — 0.3.19-SNAPSHOT

> Java 25 compile-time JDBC mapper. This document records implemented code and known limitations, **not production certification**. The canonical remaining roadmap is [issue #11](https://github.com/rfdetoni/bjorm/issues/11). A successful GitHub Actions job validates a specific commit, not all possible deployments.

## Verified capabilities

- No JPA, Hibernate, or runtime entity reflection. Generated record/POJO mappers, typed fields, projections, compiled `@Query` methods and optional ServiceLoader mapper registration.
- PostgreSQL 17 and MySQL 8.4 dialects, typed JSON codecs, `@Converted` generated JDBC value converters, `@Embedded` scalar value records, `@EnumOrdinal`, `@Transient`, audit timestamps and compile-validated `@Id` strategies.
- UUIDv7, database identity columns, assigned composite primary keys, optimistic versioned writes, versioned MySQL single-statement compare-and-swap UPSERT, PostgreSQL UPSERT, mapped `updateBy/deleteBy`, bounded batches, count/exists and projections.
- Root `@Children`, direct `@ManyToOne`, child direct `@ManyToOne`, and eager one-query UNION ALL graph hydration. Cyclic relationships, multi-hop referenced graphs and composite FKs are **not** fully supported.
- Transaction-only identity reuse, JDBC cursor streaming with transaction lifetime, explicit transaction isolation (`READ_COMMITTED`, `REPEATABLE_READ`, `SERIALIZABLE`), explicit bounded idempotent serialization/deadlock retries, and optional SQL execution timing **without SQL text or parameters**.
- Opt-in `insertDetached/upsertDetached` for safe supported shapes, retaining mutable legacy writes for source compatibility. Generated `@Children` graphs are recursively snapshotted before JDBC, including deep POJO/record children; repeated shared nodes/cycles and values without a deep-copy contract (mutable JSON/converted records) are rejected.
- `lockOne` and bounded `lockRows` (flat entities) with UPDATE/SHARE, NOWAIT and SKIP LOCKED. Row locks must belong to an existing caller-owned transaction.
- Spring Boot 4.1 integration using `TransactionAwareDataSourceProxy`; internally multi-statement operations join Spring transactions, but explicit `db.tx()` cannot nest.
- Tenant-required schema/catalog scoping, rejection of unreviewed native SQL, PostgreSQL restricted LOGIN isolation tests and MySQL per-tenant restricted-account tests. **Application routing is not a substitute for per-tenant database permissions.**
- CI tests Java 25 + PostgreSQL 17 + MySQL 8.4. The default Java-only verifier correctly emits SKIP for absent real databases; database jobs run against real drivers.

## Compatibility and security notes

The `0.x` API still mutates POJOs in legacy INSERT/UPSERT paths for compatibility. Prefer detached writes on supported shapes and discard returned proposed persisted state if an external transaction rolls back. Never mistake a successful JDBC statement for an external Spring commit.

Pass only a server-authorized tenant identifier to `forTenant` and enforce per-tenant credentials outside BJORM. `setCatalog` and PostgreSQL `search_path` are routing/scoping primitives, not authorization.

Root graph pagination currently uses a single CTE/UNION ALL plan; select joins that multiply roots are refused for pagination. Buffered reads have explicit row limits; PostgreSQL streaming needs a transaction, not just a positive fetch-size hint.

## Still pending (issue #11)

1. External Spring rollback/commit consumer guarantees and integration of production IAM, pool lifecycle, provisioning and credential revocation. The core now supports deep detached snapshots and an opt-in restricted per-tenant DataSource registry; broader orchestration remains external.
2. Recursive to-one, referenced targets with nested graphs, composite-key FKs and full arbitrary-depth relationship planner.
3. Controlled JMH/pgJDBC runs, allocation and tail-latency measurements, two-phase graph pagination comparison and real workload tuning.
4. Complete soft-delete, automatic filters, advanced JSONB, typed subqueries, formula projections, safe retries/extended RETURNING, Oracle/SQL Server dialect integration, and explicitly scoped lazy loading.

Some feature families are deliberately refused until their safety/compatibility contracts are defined; rejected shapes must not be described as implemented.

## Release policy

Stable releases are not created automatically. Snapshot publication is gated on Java, PostgreSQL and MySQL CI on `main`. Check the exact workflow and Maven consumer resolution before claiming artifacts are usable. See `ARCHITECTURE.md`, `CHANGELOG.md`, and `docs/tenant-database-permissions.md`.
