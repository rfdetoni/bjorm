# Changelog

## 0.3.2-SNAPSHOT

- Add Maven GitHub Packages snapshot publication on `main`.
- Add stable release workflow with reactor version update, tag, Maven deploy, and next development snapshot bump.
- Align all modules on `0.3.2-SNAPSHOT`; add release guards and publication documentation.


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
