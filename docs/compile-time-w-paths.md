# Compile-time W* paths and SQL execution

BJORM generates two source-level metamodels for each public `@Table` entity:

- `Product_`: existing static `Field<T>` constants, unchanged for source compatibility.
- `WProduct`: typed instance paths with a validated, explicit SQL alias.

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
