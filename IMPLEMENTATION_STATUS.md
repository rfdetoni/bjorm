# BJORM implementation status — 0.3.19-SNAPSHOT

Experimental Java 25 compile-time JDBC mapper. Not production-ready. Current repository `rfdetoni/bjorm` on `main`. This file describes code capability, not a claim of full production certification.

## Implemented

- Dependency-free JDBC core, generated CRUD/binders/readers for POJOs and records, compile-time entity paths and `@Query` repositories.
- Primary-key UUID v7, database identity returns, explicit transactions, bulk writes, versioned optimistic updates/deletes and native dialect-specific UPSERT.
- Typed DSL, property projections, `findOne`, `count`, `exists`, transaction-guarded `lockOne` (`FOR UPDATE`/`FOR SHARE`), scalar mapping and seek/offset paging in the optional Spring adapter.
- Eager `@Children` graph loading and atomic cascades. Nested/reused mapper relationships are resolved by their graph-parent slot.
- Mapped superclass columns including `@Children(mappedBy=...)` child foreign keys inherited by POJOs.
- PostgreSQL 17 and MySQL 8.4 dialects; compiled `@Query` UUID bindings and scalar UUID reads use the active dialect.
- MySQL UPSERT guards against updating a different primary-key row due to secondary UNIQUE collision when using UUID/String keys; numeric-key and versioned MySQL UPSERT remain unsupported.
- Typed JSON codecs (application-provided), with SQL NULL preserved and vendor JSON casts.
- `bjorm-spring-boot` targets Spring Boot 4.1.1; core remains Spring-independent.

## Core safety work in 0.3.19

- Primitive `Class<T>` scalar requests (e.g. `int.class`) are boxed before casting.
- `batchInsert` fails fast for `@Children` graphs, avoiding silent loss of child rows.
- UPSERT prepares each entity once, including graph persistence; `ActiveRecord.upsert` returns generated record copies.
- `find(type, null)` is rejected before obtaining a connection; `@Children(INNER)` root-filter semantics are documented.
- Mapped FK/ID types are checked with `Types.isSameType`, and SELECT paths apply a `fetchSize` hint.
- The Spring pagination adapter rejects page sizes above its configured `maxPageSize` (default: 500).

## Validation

- `.github/workflows/build.yml` has Java 25 full-reactor checks and disposable PostgreSQL 17/MySQL 8.4 jobs; snapshot publication requires all three successful.
- `scripts/verify.sh` runs compile-time negative tests, Java smoke tests, inherited field/FK checks and shared-relation graph regression, plus real JDBC integration when environment variables and drivers are available.
- Each new commit requires its own build evidence. Successful historical workflows do not validate the 0.3.19 changes.

## Known limits and pending validation

- No complete tenant routing, soft-delete policy, audit author/time, lazy loading, identity map or L2 cache. These features need explicit contracts and security review before adoption.
- Explicit JOIN pagination that multiplies roots is rejected; eager graph sorting is restricted to root columns.
- Real-world Spring consumer transaction compatibility, concurrency/load testing and reproducible PostgreSQL/MySQL performance numbers require further verification. PostgreSQL streaming still needs transactions; ordinary auto-commit SELECTs may ignore a positive fetch-size hint.
- In-memory `@Version` values remain stale after update/upsert; re-fetch before the next optimistic mutation.
- Native MySQL SQL differs from PostgreSQL: no FULL OUTER JOIN; versioned UPSERT rejected; numeric assigned-ID UPSERT rejected.
- UPSERT on arbitrary composite unique constraints and configurable NOWAIT/SKIP LOCKED are not yet covered by the typed API; use BJORM native SQL or generated `@Query` with documented transaction boundaries.
- Snapshot versions are published to GitHub Packages only after the validated GitHub Actions pipeline succeeds.

## History

See `CHANGELOG.md` for version-by-version changes and `ARCHITECTURE.md` for supported behavior and design tradeoffs.
