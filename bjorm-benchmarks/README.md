# BJORM benchmarks

JMH `JdbcMappingBenchmark` measures generated row mapping against equivalent manual mapping using the *same* simulated ResultSet. This isolates row-mapping overhead but **does not measure database latency, network, driver allocation, or throughput**. Do not use the values to claim BJORM beats Hibernate or JDBC.

Run from the Maven reactor with JDK 25:

```bash
mvn -pl bjorm-benchmarks -am package
# run generated JMH main with dependencies available on classpath; use build tooling's exec/classpath support
```

Before publishing performance numbers, add real PostgreSQL throughput runs using a shared DataSource/pool and identical SQL, transactions, dataset, warm-up, concurrency and flush policies for both BJORM and raw JDBC. Collect allocation bytes/op, p50/p95/p99, CPU, throughput, and GC, with environment and code SHA.
