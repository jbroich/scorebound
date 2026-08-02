# ADR 0001: PostgreSQL schema migrations

- Status: Accepted
- Date: 2026-08-02

## Context

Scorebound persists identity, membership, scoring, and audit history. Production
runs on PostgreSQL, deployments are automated, and historical records must not
be silently rewritten by ORM schema generation.

## Decision

- Flyway will own every production schema change.
- Versioned migrations live in `backend/src/main/resources/db/migration` and
  use descriptive names such as `V1__create_identity_tables.sql`.
- Hibernate validates the mapped schema in production and never creates or
  updates it automatically.
- PostgreSQL types and constraints are authoritative. Identifiers use UUIDs and
  timestamps use `timestamp with time zone`.
- Database constraints enforce local invariants such as positive transaction
  amounts, one cancellation per transaction, and valid period dates.
- Cross-record and authorization invariants remain in application services and
  are covered by PostgreSQL integration tests.
- The lightweight H2 context test is only a fast wiring check. Persistence and
  migration behavior must be tested against PostgreSQL in CI when persistence
  is introduced.
- Applied migrations are never edited. Corrections use a new forward migration.

## Consequences

Deployments must run migrations before the new application revision becomes
healthy. A revision that cannot validate the migrated schema must not receive
traffic. Application rollback is supported only when the previous revision is
compatible with the forward-migrated schema, so destructive schema changes need
an expand-and-contract sequence across releases.

Automated database backups remain a separate deferred issue; their absence does
not permit destructive migrations without an explicit migration and recovery
plan.
