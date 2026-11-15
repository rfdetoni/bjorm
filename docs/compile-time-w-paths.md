# Compile-time W* paths and SQL execution

BJORM generates one typed query metamodel, `WProduct`, and one JDBC infrastructure
mapper, `Product_BjormMapper`, for each public `@Table` entity. The legacy
`Product_` metamodel is no longer generated. Neither generated type uses runtime
entity reflection, proxies, dirty checking, sessions or a second-level cache.

## Usage

```java
WProduct p = WProduct.product; // t0, generated at compile time
List<Product> rows = WProduct.find(db) // explicit Operations/Bjorm context
    .where(p.name.startsWith("Pen").and(p.price.gte(new BigDecimal("10"))))
    .orderBy(p.name.asc())
    .limit(50)
    .fetch();

// With a custom root alias, use selectFrom:
WProduct named = WProduct.product.as("p");
List<Product> namedRows = db.selectFrom(named)
    .where(named.name.eq("Pencil"))
    .fetch();
```

`WProduct.find(db)` is a generated static shortcut for
`WProduct.product.select(db)`, which delegates to the regular BJORM query
builder. It accepts `Operations` so it works with a scoped `Bjorm`
or an explicit BJORM transaction, without a static connection or
thread-local session. For predicates use `.eq(value)`, not Java
`.equals(value)`. The annotation processor **cannot add** static
`find()` methods to the existing `Product` type.

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

Manually supplied aliases and native `@Query` statements are preserved.
The generated typed path is the only metamodel:

```java
WProduct p = WProduct.product;
WProduct.find(db).where(p.name.eq("Pencil")).fetch();
```

Do not convert a native SQL query to a fluent DSL if doing so harms
query plans, projections, authorization or database-specific capabilities.
Use `@Query` for fixed SQL and `db.query` for reviewed native SQL.
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


In tenant-required mode, compiled `@Query` methods still use native SQL.
They require an explicitly authorized, tenant-scoped context with
`permitReviewedTenantNativeSql()` and database permissions that prevent
cross-tenant access. SQL compilation alone does not constitute authorization.


## Root-only reads for expensive entity graphs

Mapped `@Children` and `@ManyToOne` relations are eagerly hydrated by default.
When only root columns are required, use `.withoutRelations()` on a typed
`Select<T>`: `WOrder.find(db).withoutRelations().where(WOrder.order.id.eq(id)).fetch()`.
This uses one flat SELECT and skips graph `JOIN`/`UNION ALL` expansion.
The returned entity's relation properties are not hydrated; avoid depending
on them until explicitly fetched. Root-only selections also support
`withStream`, whose cursor must be consumed inside its callback.
