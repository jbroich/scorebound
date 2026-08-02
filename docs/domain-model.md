# Domain model

This document defines the MVP domain boundaries, records, lifecycles, and
invariants. It deliberately describes behavior before selecting persistence or
web-framework details.

## Bounded areas

- **Identity** owns accounts, credentials, roles, sessions, and account-to-
  member links.
- **Teams** owns teams, members, and dated membership history.
- **Competition** owns scoreboards, team selection, periods, and period
  participants.
- **Scoring** owns immutable score transactions, cancellations, and standings.
- **Display** owns per-display board selection, rotation, sound preference, and
  live presentation events.

The first implementation can keep these areas in one Spring Boot application.
Their package and service boundaries should still prevent controllers from
directly manipulating another area's persistence records.

## Identity

### Account

| Field | Rule |
| --- | --- |
| `id` | Stable UUID |
| `username` | Required, unique ignoring case, normalized before comparison |
| `passwordHash` | Never returned by an API or written to logs |
| `enabled` | Disabled accounts cannot start or retain sessions |
| `mustChangePassword` | Set for bootstrap and admin-issued temporary passwords |
| `memberId` | Optional link to one member |
| `roles` | Non-empty set of `Admin`, `Scorer`, `Member`, `Display` |
| `preferredLocale` | Optional `en` or `de` preference |
| audit fields | Creation and last modification time and actor |

An account can have several roles. A session opened in display mode receives
only display permissions, even if the account has additional roles. This keeps
the longer display session from inheriting administrative access.

### Assignment

- A scorer assignment links an account with the scoreboards on which it may
  create and cancel its own score transactions.
- A display assignment links an account with the scoreboards it may present.
- An account with `Admin` can manage every scoreboard.
- A linked `Member` account can view scoreboards in which the member's active
  team participates.

## Teams

### Team

| Field | Rule |
| --- | --- |
| `id` | Stable UUID |
| `name` | Required human-readable name |
| `shortName` | Required compact display name |
| `color` | Required sRGB color in `#RRGGBB` form |
| `image` | Optional managed image reference, never an arbitrary file path |
| `active` | Inactive teams remain available to historical records |

Names and short names are unique among active teams, ignoring case.

### Member

| Field | Rule |
| --- | --- |
| `id` | Stable UUID |
| `displayName` | Required and used in wall-display activity |
| `firstName` / `lastName` | Optional |
| `active` | Inactive members cannot receive new personal transactions |

### Membership

A membership contains `memberId`, `teamId`, `validFrom`, and an optional
`validUntil`. A member has exactly one open membership at a time. Changing teams
closes the existing membership and opens the next one in a single transaction.
Past memberships are never rewritten or deleted.

## Competition

### Scoreboard

A scoreboard contains a stable UUID, name, optional description, active flag,
and ordered selection of teams for future periods. Removing a selected team
only excludes it from future periods; it does not remove period participation.

### Competition period

| Field | Rule |
| --- | --- |
| `id` | Stable UUID |
| `scoreboardId` | Owning scoreboard |
| `name` | Required, for example `Training Year 2026` |
| `startsAt` / `endsAt` | Timezone-aware instants with start before end |
| `status` | `Scheduled`, `Active`, or `Closed` |
| `closedAt` / `closedBy` | Records automatic or manual closure |
| `visualCeiling` | Monotonic display scale, never below the current leader |

A scoreboard has at most one active period. Activation snapshots its selected
teams into period participants. A newly selected team can also join an active
period explicitly. A period participant is never hard-deleted.

Automatic lifecycle processing activates the next eligible scheduled period
and closes an active period at its end. Manual close is allowed for an Admin.
Reopening is allowed only when the scoreboard has no other active period and
restores the same participants and transactions.

Every period begins at zero because standings are scoped to its transaction
ledger; scores never carry over.

## Scoring

### Score transaction

| Field | Rule |
| --- | --- |
| `id` | Stable UUID |
| `periodId` | Required active period |
| `teamId` | Required team snapshot and period participant |
| `memberId` | Optional eligible member target |
| `kind` | `Credit` or `Debit` |
| `amount` | Integer from 1 through 1000 |
| `reason` | Required non-blank text |
| `createdAt` / `createdBy` | Immutable audit data |
| `idempotencyKey` | Optional caller key, unique per actor |

For a member target, `teamId` is resolved from the member's active membership
when the transaction is created. It is stored permanently so later membership
changes do not move historical points.

### Cancellation

A cancellation is a separate one-to-one record containing the original
transaction ID, required reason, actor, and time. The original transaction is
not updated or deleted. A scorer may cancel a transaction they created; an
Admin may cancel any transaction. A cancellation cannot itself be cancelled.
An Admin corrects a mistaken cancellation with a new score transaction.

### Standings

For a team and period:

```text
score = sum(non-cancelled credits) - sum(non-cancelled debits)
```

All period participants appear even when their score is zero. A debit is
accepted only if the resulting score is non-negative. Concurrent writes lock
the affected period-participant standing while validating and recording the
transaction, preventing two individually valid debits from producing a
negative result.

Teams are ordered by score descending. Equal scores receive the same rank using
competition ranking (`1, 1, 3`), without a tie-breaker. Every team at rank one
when a period closes is a winner.

## Authorization matrix

| Operation | Admin | Assigned Scorer | Linked Member | Assigned Display |
| --- | :---: | :---: | :---: | :---: |
| Manage accounts and roles | Yes | No | No | No |
| Manage teams, members, and periods | Yes | No | No | No |
| View an authorized scoreboard | Yes | Yes | Team participates | Yes |
| Create a score transaction | Yes | Yes | No | No |
| Cancel own transaction | Yes | Yes | No | No |
| Cancel another actor's transaction | Yes | No | No | No |
| Open wall-display presentation | Yes | No | No | Yes |

All access requires a valid session. Disabling an account invalidates all its
sessions. Authorization is enforced in application services as well as at the
HTTP boundary.

## Retention and deletion

Records referenced by membership, participation, period, transaction, or audit
history are deactivated instead of hard-deleted. Temporary unused configuration
may be hard-deleted only when no historical reference exists. Uploaded team
images must be validated, size-limited, and replaced through managed storage.
