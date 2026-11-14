# Compile-time W* paths and SQL execution

BJORM generates two source-level metamodels for each public `@Table` entity:

- `Product_`: existing static `Field<T>` constants, unchanged for source compatibility.
- `WProduct`: typed instance paths with a shared, deterministic SQL alias policy.

Neither metamodel uses runtime entity reflection, proxies, dirty checking, sessions or a second-level entity cache.

## Usage

```java
WProduct p = WProduct.product.as("p");
List<Product> rows = db.selectFrom(p)
    .where(p.name.startsWith("Pen").and(p.price.gte(new BigDecimal("10"))))
    .orderBy(p.name.asc())
    .limit(50)
    .fetch();
```

`p.name` is a `Paths.StringPath`, `p.price` is a `Paths.NumberPath<BigDecimal>`.
`contains`, `startsWith`, and `endsWith` escape SQL LIKE wildcards and use
a bound parameter. `W*` fields use the same columns as the generated mapper,
including renamed columns and flattened embedded scalar fields. `@Converted`,
`@Json` and `@EnumOrdinal` remain plain `Field<T>` paths to avoid
inventing unsupported string/numeric semantics.

## Unified SQL alias policy

All BJORM fluent `Select` queries use `t0` for the root entity by default,
including `db.select(Entity.class)`, projections, and generated `W*` paths.
Explicit root aliases through `.as("p")` remain supported.

Automatic typed joins allocate `t1`, `t2`, etc., in insertion order. They
skip aliases reserved by explicit JOINs or by an explicit root alias. The
callback receives the correct compile-time typed joined path, so callers
never construct an alias string:

```java
WProduct product = WProduct.product; // automatically t0
List<Product> rows = db.selectFrom(product)
    .join(WUser.user, user -> product.ownerId.sameAs(user.id)) // user is t1
    .where(product.name.eq("Pen"))
    .fetch();
```

Use `.leftJoin(path, on)`, `.rightJoin(path, on)`, or `.fullJoin(path, on)`
for other explicit join types. A repeated join to the same entity receives a
new alias; aliases are per query, not globally allocated. Eager graph joins
follow the same positional numbering within their generated SELECT scope.
Nested CTE SELECTs start a fresh scope with `t0`.

Manually supplied aliases and native `@Query` statements are preserved. The
existing static `Entity_` fields remain unqualified for source compatibility;
use the `W*` path API when a join requires unambiguous, qualified references.
Fixed SQL CRUD statements and manual `@Query` SQL are not automatically
rewritten.

## When is SQL created?

- **At compilation**: generated CRUD mapper statements, bindings/readers, and
  repositories annotated with `@Query` are generated at `javac` time;
  SQL string literals are part of the generated class.
- **At query execution**: the fluent `Select` DSL combines optional
  predicates, explicit JOINs and ORDER BY into parameterized SQL.
  This is necessary when query shape depends on runtime conditions. It is
  not a promise that arbitrary Java DSL expressions are compiled into SQL
  by the annotation processor.

Use `@Query` for frequently executed fixed SQL shapes. Use the fluent DSL
for conditional filters and dynamically composed queries. Both paths use
direct JDBC with generated mappers; adding a global query-shape cache or
an entity lifecycle manager is not part of this change.

JOIN pagination, graph shape limits, and transaction ownership continue
to follow the existing BJORM contracts. Apply tenant authorization through
the caller's authorized `Bjorm.forTenant(...)` context.
