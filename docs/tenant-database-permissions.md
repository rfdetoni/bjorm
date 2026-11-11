# Database-enforced tenant isolation (PostgreSQL 17 / MySQL 8.4)

BJORM validates tenant schema identifiers, scopes PostgreSQL `SET LOCAL search_path`
to the active transaction, resets MySQL catalogs, and **denies native SQL by
default** in `tenantRequired` contexts. These are defense-in-depth mechanisms,
**not** a substitute for database privileges.

## Production boundary

**Use a database login/pool with access only to the authorized tenant's schema
(or catalog).** A single shared superuser, table owner or role with privileges in
every tenant can access cross-tenant data through fully qualified SQL,
independently of `search_path`. A shared connection pool with a role allowed to
`SET ROLE` to *every* tenant is **not** an isolation boundary against malicious
native SQL: that connection can switch to another permitted role.

Provision tenant membership and schema ownership through trusted IAM,
provisioning and migration processes, not arbitrary client headers or paths.
Connect the application with a separate least-privilege credential, and pass
only the corresponding authorized schema to `forTenant(schema)`.

Avoid globally sharing a tenant-privileged `Bjorm` instance between requests.
Use separate `DataSource` pools per tenant credential (with bounded pool sizes
and idle eviction), or an explicitly audited intermediary that provides the
same database-enforced restriction. A shared all-tenant datasource offers
application-level routing only, not hard database-level isolation.

## PostgreSQL: provisioning example (execute as migration administrator)

Use unique schema and role identifiers from a trusted registry. Example values
are illustrative; parameterize **identifiers in provisioning code only after
strict whitelist validation**. Do not bind identifiers as JDBC values.

```sql
-- 1. A separate non-login owner executes migrations, never application DML.
CREATE ROLE bjorm_tenant_a_owner NOLOGIN;
CREATE ROLE bjorm_tenant_a_app LOGIN PASSWORD 'replace-with-rotated-secret'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOINHERIT;

CREATE SCHEMA tenant_a AUTHORIZATION bjorm_tenant_a_owner;
REVOKE ALL ON SCHEMA tenant_a FROM PUBLIC;
GRANT USAGE ON SCHEMA tenant_a TO bjorm_tenant_a_app;

-- 2. Execute all tenant_a DDL as the owner/migration identity.
SET ROLE bjorm_tenant_a_owner;
CREATE TABLE tenant_a.customers (
    id uuid PRIMARY KEY,
    name text NOT NULL
);
RESET ROLE;

-- 3. Grant the minimum table privileges needed by runtime workflows.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA tenant_a
    TO bjorm_tenant_a_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA tenant_a
    TO bjorm_tenant_a_app;

-- 4. Grants for tables created in future migrations by the owner.
ALTER DEFAULT PRIVILEGES FOR ROLE bjorm_tenant_a_owner IN SCHEMA tenant_a
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO bjorm_tenant_a_app;
ALTER DEFAULT PRIVILEGES FOR ROLE bjorm_tenant_a_owner IN SCHEMA tenant_a
    GRANT USAGE, SELECT ON SEQUENCES TO bjorm_tenant_a_app;
```

**Do not** grant `USAGE`, table rights, schema ownership, membership in other
tenant roles, `BYPASSRLS` or `SUPERUSER` to `bjorm_tenant_a_app` outside
the assigned tenant. Do not run BJORM with the provisioning or migration role.

For `@Table(schema="...")` fixed-schema mappings, use a separately reviewed
infrastructure context: BJORM deliberately refuses fixed schemas in
`tenantRequired` mode.

Test with the exact app credential (not with `SET ROLE` on a superuser session):

```sql
SELECT * FROM tenant_a.customers; -- allowed
SELECT * FROM tenant_b.customers; -- ERROR: permission denied (SQLSTATE 42501)
UPDATE tenant_b.customers SET name = 'attack'; -- ERROR 42501
```

The automated `PostgresRoleIsolationTest` provisions a unique temporary
`LOGIN` role and two schemas, confirms the real credentials and tests
qualified cross-schema reads/writes even after reviewed-native-SQL opt-in.
It runs in the PostgreSQL CI job. To execute locally, use an **expendable test
database** with role/schema creation rights and set
`BJORM_ROLE_TEST_ADMIN=true`, `BJORM_TEST_JDBC_URL`, `BJORM_TEST_USER`,
and `BJORM_TEST_PASSWORD`. The test deletes its own schemas and role.

## MySQL: equivalent database-level restriction

MySQL uses catalogs/databases rather than PostgreSQL schemas. Provision
one account per tenant database:

```sql
CREATE DATABASE tenant_a;
CREATE USER 'bjorm_tenant_a_app'@'%' IDENTIFIED BY 'replace-with-rotated-secret';
GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_a.*
    TO 'bjorm_tenant_a_app'@'%';
-- If auto-increment/other features require extra rights, grant narrowly.
```

Avoid `*.*` DML grants and any grants on `tenant_b.*` for that account.
Review `SHOW GRANTS FOR 'bjorm_tenant_a_app'@'%'`, including inherited roles;
use TLS, dedicated users, rotated secrets and a limited network source instead
of wildcard host access where possible.

## Migration and failure checks

1. Provision the new schema and owner with an administrative connection.
2. Apply schema DDL as the migration role, configure current/future grants.
3. Verify the application login can read/write only its own mapped tables.
4. Verify qualified SQL against an unrelated schema fails at the JDBC layer,
   even if a developer explicitly opts in to reviewed native SQL.
5. Change the application datasource to the restricted credential before
   enabling the tenant; revoke old broad credentials after rollout.
6. Monitor role/tenant provisioning drift and revoke privileges when a tenant
   is deprovisioned. Keep migrations separate from normal traffic.

**Integration coverage:** The MySQL 8.4 CI job provisions two disposable
catalogs and two real LOGIN accounts, each granted CRUD privileges exclusively
on its own database. `MySqlRoleIsolationTest` checks allowed reads/writes and
real JDBC privilege denial for qualified `SELECT`, `INSERT`, `UPDATE` and
`DELETE`, including reviewed native SQL and attempts to bind the other tenant.
Both accounts and databases are removed in a `finally` block. To run locally,
use an **expendable MySQL instance**, set `BJORM_MYSQL_ROLE_TEST_ADMIN=true`,
`BJORM_MYSQL_JDBC_URL`, `BJORM_MYSQL_ADMIN_USER`,
`BJORM_MYSQL_ADMIN_PASSWORD`, and configure the MySQL JDBC driver JAR in
`BJORM_MYSQL_DRIVER_JAR`. This does not validate application-level IAM,
rotation, production pool eviction, or a managed-cloud provisioning workflow;
those integrations must be verified for each deployment.

PostgreSQL also tests temporary restricted login isolation.

## Audit timestamps and safe detached writes

`insertDetached(entity)` and `upsertDetached(entity)` copy all generated
mapped POJO properties using compile-time accessors. They never mutate the
caller-owned instance even if the statement fails or the transaction rolls
back. The returned copy includes generated IDs and audit fields after a
successful statement, but in an **external transaction** it does not prove the
transaction has committed. Discard that returned copy on rollback.

These APIs intentionally reject `@Children` graphs; mutable nested child
objects would require deep graph copying and their own atomicity guarantees.
Existing `insert`, `insertReturning` and `upsertReturning` retain their
0.x in-place POJO materialization semantics for compatibility. For immutable
records, the detached methods reuse the normal immutable copy behavior.
