# BJORM performance benchmarks

All benchmarks use JMH 1.37 and Java 25. **No published throughput or
latency numbers are claimed by this repository until measured.**

## Build and run

```bash
mvn -B -ntp -pl bjorm-core,bjorm-processor -am -DskipTests install
mvn -B -ntp -pl bjorm-benchmarks -am package
mvn -B -ntp -pl bjorm-benchmarks dependency:build-classpath \
  -Dmdep.outputFile=target/jmh-classpath.txt
CP="bjorm-benchmarks/target/classes:$(cat bjorm-benchmarks/target/jmh-classpath.txt)"

# UUID v7 vs UUID.randomUUID() (v4) throughput:
java -cp "$CP" org.openjdk.jmh.Main '.*UuidGenerationBenchmark.*'

# Generated JDBC mapping vs manual mapping on the SAME simulated ResultSet:
java -cp "$CP" org.openjdk.jmh.Main '.*JdbcMappingBenchmark.*'
```

The simulated `JdbcMappingBenchmark` isolates row-mapping overhead; it
**does not** measure database latency, network I/O, pooling, allocations from
a real driver or total query throughput. Do not use it to claim BJORM is
faster than Hibernate or raw JDBC for production workloads.

## PostgreSQL 17: real JDBC round-trip and graph hydration

`PostgresRoundtripBenchmark` uses **one real PostgreSQL connection per JMH
worker thread**, both for raw JDBC and for the generated BJORM mapper.
Each connection owns three **TEMP tables** populated during setup. No
permanent database schema or fixture is changed. Use a non-production
database and a **non-admin** account with TEMP privileges.

```bash
export BJORM_BENCH_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/bjorm_bench'
export BJORM_BENCH_USER='bjorm_bench'
export BJORM_BENCH_PASSWORD='use-a-local-secret'

java -cp "$CP" org.openjdk.jmh.Main \
  '.*PostgresRoundtripBenchmark.*' \
  -rf json -rff bjorm-roundtrip-results.json
```

The JMH parameters sweep:

- `prepareThreshold=0/5` — pgJDBC server-prepared statement policy.
- `preparedStatementCacheQueries=0/256` — driver per-connection statement
  query cache. Test this before introducing an application-level cache.
- `childCount=1/32/256` — rows hydrated in the root-child JOIN.

The four operations compare BJORM and manual JDBC flat lookup, and BJORM
and **manual one-query LEFT JOIN** graph hydration, using the same session
and values. Graph SQL shapes are not identical: their results compare
end-to-end semantics rather than the pure cost of mapping.

**Benchmarks are not part of CI's default test run**: each sweep contains
multiple JMH forks/warmups and needs a controlled machine for repeatability.
The Java CI verifies that the source compiles; real PostgreSQL/MySQL
integration tests separately validate correctness.

## Measurement protocol

Run on a dedicated machine with a pinned JDK/JVM, PostgreSQL version,
pgJDBC version and repository SHA. Record:

1. Warm-up iterations, fork/thread count, driver parameters, query plan,
   table row counts and database configuration.
2. Throughput and p50/p95/p99 latency under representative concurrency;
   JMH's simple `avgt` mode alone does **not** provide tail-latency percentiles.
3. Allocation bytes/op, GC counts, CPU utilization and RSS/process memory
   using a consistent profiler.
4. End-to-end data access latency against both single-key and nested graph
   datasets. Do not infer gains from a synthetic mapping microbenchmark.
5. Controlled before/after comparisons with confidence intervals before
   enabling custom caches or changing query planner defaults.

If the prepared statement driver cache already recovers most of the
performance, prefer the driver behavior: a BJORM-managed statement cache
would add resource ownership, lifetime and transaction invalidation risks.
