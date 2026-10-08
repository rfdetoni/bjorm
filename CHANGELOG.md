# Changelog

## 0.3.4-SNAPSHOT

- Generate direct PreparedStatement.setBigDecimal for BigDecimal values instead of setObject.
- Align JMH manual mapper with generated primitive NULL checks; add wasNull support to the test ResultSet.
- Preserve JDBC core simplicity; only introduce runtime optimizations after controlled performance measurements.

## 0.3.3-SNAPSHOT

- Move Java API, annotation processor, generated classes, SPI registrations and Spring adapter from `dev.bjorm` to `com.github.rfdetoni.bjorm`.
- Align Maven groupId across all modules and bump to `0.3.3-SNAPSHOT`.
- Document Java SE `javax.sql` / `javax.annotation.processing` compatibility; add SPI namespace verification.
- Keep GitHub Packages Maven snapshot and stable release automation.

- Add Maven GitHub Packages snapshot publication on `main`.
- Add stable release workflow with reactor version update, tag, Maven deploy, and next development snapshot bump.
- Align all modules on the current development snapshot; add release guards and publication documentation.


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
