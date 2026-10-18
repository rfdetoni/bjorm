# BJORM implementation status — 0.3.17-SNAPSHOT

Experimental Java 25 compile-time JDBC mapper. Not production-ready. Current repository `rfdetoni/bjorm` on `main`. This file describes code capability, not a claim of full production certification.

## Implemented

- Dependency-free JDBC core, generated CRUD/binders/readers for POJOs and records, compile-time entity paths and `@Query` repositories.
- Primary-key UUID v7, database identity returns, explicit transactions, bulk writes, versioned optimistic updates/deletes and native dialect-specific UPSERT.
- Typed DSL, property projections, `findOne`, scalar mapping, seek/offset paging in the optional Spring adapter.
- Eager `@Children` graph loading and atomic cascades. Nested/reused mapper relationships are resolved by their graph-parent slot.
- Mapped superclass columns including `@Children(mappedBy=...)` child foreign keys inherited by POJOs.
- PostgreSQL 17 and MySQL 8.4 dialects; compiled `@Query` UUID bindings and scalar UUID reads use the active dialect.
- MySQL UPSERT guards against updating a different primary-key row due to secondary UNIQUE collision when using UUID/String keys; numeric-key and versioned MySQL UPSERT remain unsupported.
- Typed JSON codecs (application-provided), with SQL NULL preserved and vendor JSON casts.
- `bjorm-spring-boot` targets Spring Boot 4.1.1; core remains Spring-independent.

## Validation

- `.github/workflows/build.yml` has Java 25 full-reactor checks and disposable PostgreSQL 17/MySQL 8.4 jobs; snapshot publication requires all three successful.
- `scripts/verify.sh` runs compile-time negative tests, Java smoke tests, inherited field/FK checks and shared-relation graph regression, plus real JDBC integration when environment variables and drivers are available.
- The previous `main` revision `2e2c654` had successful remote `build` and `publish-snapshot` runs on 2026-10-09. The 0.3.17 revision needs a fresh successful run; do not infer new validation from the old commit.

## Known limits and pending validation

- No complete tenant routing, soft-delete policy, audit author/time, lazy loading, identity map or L2 cache. These are application-specific or deliberately out of scope.
- Explicit JOIN pagination that multiplies roots is rejected; eager graph sorting is restricted to root columns.
- Real-world Spring consumer transaction compatibility, concurrency/load testing and reproducible PostgreSQL/MySQL performance numbers require further verification.
- Native MySQL SQL differs from PostgreSQL: no FULL OUTER JOIN; versioned UPSERT rejected; numeric assigned-ID UPSERT rejected.
- Snapshot versions are published to GitHub Packages only after the validated GitHub Actions pipeline succeeds.

## History

See `CHANGELOG.md` for version-by-version changes and `ARCHITECTURE.md` for supported behavior and design tradeoffs.
