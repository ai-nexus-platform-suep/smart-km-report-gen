# Database Guidelines

> Database patterns and conventions for this project.

---

## Overview

<!--
Document your project's database conventions here.

Questions to answer:
- What ORM/query library do you use?
- How are migrations managed?
- What are the naming conventions for tables/columns?
- How do you handle transactions?
-->

(To be filled by the team)

---

## Query Patterns

<!-- How should queries be written? Batch operations? -->

(To be filled by the team)

---

## Migrations

<!-- How to create and run migrations -->

(To be filled by the team)

---

## Naming Conventions

<!-- Table names, column names, index names -->

(To be filled by the team)

---

## Common Mistakes

<!-- Database-related mistakes your team has made -->

(To be filled by the team)

---

## Scenario: Flyway Migration Repair For Stale Local Databases

### 1. Scope / Trigger

- Trigger: adding or changing Flyway scripts under `km-backend/src/main/resources/db/migration`.
- This is a database contract because local developer databases may already have `flyway_schema_history` entries from older versions of `V1__init_km.sql`, so newly required tables cannot rely only on editing an already-applied migration.

### 2. Signatures

- Migration directory: `km-backend/src/main/resources/db/migration`.
- Schema history table: `flyway_schema_history` in the configured MySQL schema.
- Relevant backend config: `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`, and `spring.flyway.*` in `km-backend/src/main/resources/application.yml`.

### 3. Contracts

- Do not assume editing an already-applied Flyway version will update existing databases.
- If a versioned migration has already been shared, avoid editing it because existing databases that applied it successfully can fail Flyway checksum validation.
- If a later migration seeds or depends on a table that may be missing in older local databases, prefer a new versioned repair migration before new dependent statements; if the missing table blocks the already-failed migration itself, repair the local database manually with the smallest table DDL.
- `V2__seed_system_config.sql` depends on `system_config`; stale local databases that applied an older `V1` without this table must create `system_config` manually before retrying `V2`.
- For a failed migration, remove only the failed row from `flyway_schema_history` before retrying startup.

### 4. Validation & Error Matrix

- `Table '<db>.system_config' doesn't exist` during `V2__seed_system_config.sql` -> create the missing table manually, remove the failed Flyway history row for version `2`, then restart.
- `Found failed migration` on startup -> repair the failed Flyway record, then restart.
- Checksum mismatch for a previously successful migration -> restore the migration file to the applied content or deliberately run Flyway repair only in local development after confirming the SQL is equivalent.
- Empty disposable local database -> Flyway should run V1 then V2 successfully without manual SQL.

### 5. Good/Base/Bad Cases

- Good: a clean database runs current V1, creates `system_config`, then V2 seeds default rows.
- Base: developer fixes a stale failed local V2 by creating `system_config`, deleting the failed `flyway_schema_history` row for version `2`, and restarting.
- Bad: editing V1 to add `system_config` and expecting an existing database that already applied V1 to get the new table automatically.
- Bad: editing a shared V2 migration to create `system_config`, causing checksum mismatch for databases where V2 already succeeded.

### 6. Tests Required

- Inspect migration order and verify dependent tables are created before seed inserts.
- On a clean database, start backend and confirm V1/V2 complete.
- On a stale local database with V1 recorded but no `system_config`, create the missing table, remove the failed V2 history row, restart backend, and confirm V2 inserts `embedding`, `rerank`, and `parser` rows.
- Run `mvn -pl km-backend -am compile` when Maven is available.

### 7. Wrong vs Correct

#### Wrong

```sql
INSERT INTO system_config (config_key, config_value) VALUES ('parser', JSON_OBJECT('backend', 'tika'));
```

#### Correct

```sql
CREATE TABLE IF NOT EXISTS `system_config` (
    `config_key` VARCHAR(64) NOT NULL PRIMARY KEY,
    `config_value` JSON NOT NULL,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELETE FROM flyway_schema_history WHERE version = '2' AND success = 0;
```

## Scenario: MySQL Knowledge-Management Schema Initialization

### 1. Scope / Trigger

- Trigger: initializing or repairing MySQL tables for `km-backend` knowledge-base, document, chunk, and system configuration features.
- This is a database contract because H2 and MySQL use different column types, and Flyway baselining can skip `V1__init_km.sql` when the shared `user` table already exists.

### 2. Signatures

- Normal MySQL migrations: `km-backend/src/main/resources/db/migration/V1__init_km.sql` and `V2__seed_system_config.sql`.
- Manual local MySQL repair: `docs/mysql-km-schema.sql`.
- H2-only test/dev schema: `km-backend/src/main/resources/schema-h2.sql`.
- Required Flyway config: `spring.flyway.baseline-on-migrate: true` with `spring.flyway.baseline-version: 0` in `km-backend/src/main/resources/application.yml`.

### 3. Contracts

- Do not run `schema-h2.sql` against MySQL; it may use H2-only types such as `CLOB`.
- MySQL schema must use MySQL-compatible types: `JSON` for JSON fields, `TEXT` for chunk content, and `DATETIME` for timestamps.
- `docs/user.sql` owns the shared `user` table and is separate from KM-specific Flyway migrations.
- `baseline-version: 0` is required so a non-empty schema containing only shared tables can still execute Flyway `V1`.
- `docs/mysql-km-schema.sql` is for local manual repair/init only; normal startup should rely on Flyway.

### 4. Validation & Error Matrix

- `Table '<db>.knowledge_base' doesn't exist` when creating a KB -> V1 was skipped or not run; execute `docs/mysql-km-schema.sql` locally or fix Flyway baseline state.
- MySQL rejects `CLOB` -> wrong script was used; use `docs/mysql-km-schema.sql` or Flyway migrations, not `schema-h2.sql`.
- Flyway baseline exists at version `1` but KM tables are missing -> local schema was baselined too high; repair manually with `docs/mysql-km-schema.sql` or rebuild a disposable local schema.
- Clean MySQL schema with only `user` table -> backend startup should baseline at `0`, run V1, then run V2.

### 5. Good/Base/Bad Cases

- Good: clean MySQL with `docs/user.sql` already applied starts backend and Flyway creates KM tables because `baseline-version` is `0`.
- Base: stale local DB missing KM tables is repaired by running `docs/mysql-km-schema.sql` once.
- Bad: copying H2 `CLOB` DDL into MySQL and expecting it to work.
- Bad: changing existing Flyway V1/V2 files to repair one local database, causing checksum issues elsewhere.

### 6. Tests Required

- Inspect `docs/mysql-km-schema.sql` for absence of `CLOB` and `CREATE INDEX IF NOT EXISTS`.
- Compare manual MySQL DDL against current Flyway V1/V2 for table names, indexes, foreign keys, and seed rows.
- On a local repaired schema, call `POST /api/knowledge-bases` and verify `knowledge_base` insert succeeds.
- Run `mvn -pl km-backend -am compile` when Maven is available.

### 7. Wrong vs Correct

#### Wrong

```sql
CREATE TABLE knowledge_base (
    chunk_strategy_json CLOB NOT NULL
);
```

#### Correct

```sql
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `chunk_strategy_json` JSON NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
