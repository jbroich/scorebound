# HTTP API contract

The MVP exposes a same-origin JSON API below `/api/v1`. The contract below is
the implementation target; an OpenAPI document should be generated or checked
against it when controllers are introduced.

## Conventions

- UUIDs are opaque strings and timestamps use ISO 8601 UTC instants.
- JSON uses lower camel case.
- Requests and responses use `application/json`, except image upload and the
  server-sent event stream.
- Authentication uses a secure, HTTP-only, same-site session cookie.
- State-changing browser requests require a CSRF token.
- Collection endpoints accept `page`, `size`, and documented sort parameters.
- Transaction history sorts by `createdAt` descending and then `id` descending.
- Unsupported fields are rejected instead of silently ignored.
- Validation failures use RFC 9457 Problem Details with stable application
  error codes and field-level violations.

Example error:

```json
{
  "type": "https://scorebound.joelbroich.de/problems/score-would-be-negative",
  "title": "Score would be negative",
  "status": 409,
  "code": "score_would_be_negative",
  "detail": "The debit exceeds the team's current score.",
  "instance": "/api/v1/scoreboards/7d3.../transactions",
  "violations": []
}
```

## Sessions

| Method and path | Purpose |
| --- | --- |
| `POST /sessions` | Authenticate with username, password, and `Normal` or `Display` mode |
| `GET /session` | Return the current account, effective roles, assignments, locale, and CSRF data |
| `DELETE /session` | End the current session |
| `PUT /session/password` | Replace the current or temporary password |

Normal sessions expire after at most 12 hours. Display mode requires the
`Display` role, exposes only display permissions, and can last up to 30 days.
Accounts marked `mustChangePassword` can access only session inspection,
password replacement, and logout.

## Administrative resources

| Method and path | Purpose | Access |
| --- | --- | --- |
| `GET /accounts` | Page through accounts | Admin |
| `POST /accounts` | Create an account with a temporary password | Admin |
| `GET /accounts/{accountId}` | Read account roles and assignments | Admin or self |
| `PATCH /accounts/{accountId}` | Change status, roles, locale, or member link | Admin |
| `POST /accounts/{accountId}/temporary-password` | Issue a new temporary password | Admin |
| `PUT /accounts/{accountId}/scorer-assignments/{scoreboardId}` | Assign scoring access | Admin |
| `DELETE /accounts/{accountId}/scorer-assignments/{scoreboardId}` | Remove scoring access | Admin |
| `PUT /accounts/{accountId}/display-assignments/{scoreboardId}` | Assign display access | Admin |
| `DELETE /accounts/{accountId}/display-assignments/{scoreboardId}` | Remove display access | Admin |

Temporary passwords are returned only once in the successful creation or reset
response and are never retrievable later.

## Teams and members

| Method and path | Purpose | Access |
| --- | --- | --- |
| `GET /teams` | List authorized active teams | Authenticated |
| `POST /teams` | Create a team | Admin |
| `GET /teams/{teamId}` | Read a team | Authorized viewer |
| `PATCH /teams/{teamId}` | Change team presentation or status | Admin |
| `GET /teams/{teamId}/image` | Read the optional managed image | Authorized viewer |
| `PUT /teams/{teamId}/image` | Validate and replace the optional image | Admin |
| `DELETE /teams/{teamId}/image` | Remove the optional image | Admin |
| `GET /members` | Page through authorized members | Authenticated |
| `POST /members` | Create a member and initial membership | Admin |
| `GET /members/{memberId}` | Read member and membership history | Authorized viewer |
| `PATCH /members/{memberId}` | Change member profile or status | Admin |
| `POST /members/{memberId}/team-changes` | Close the active membership and join another team | Admin |

The team-change command accepts the new `teamId` and effective instant. An MVP
request cannot backdate a change before the current membership began.
Team images are sent as raw PNG, JPEG, or WebP request bodies, are limited to
2 MB, and are verified against their file signature before storage.

## Scoreboards and periods

| Method and path | Purpose | Access |
| --- | --- | --- |
| `GET /scoreboards` | List scoreboards visible to the session | Authenticated |
| `POST /scoreboards` | Create a scoreboard | Admin |
| `GET /scoreboards/{scoreboardId}` | Read configuration and active period summary | Authorized viewer |
| `PATCH /scoreboards/{scoreboardId}` | Change name, description, or status | Admin |
| `PUT /scoreboards/{scoreboardId}/teams/{teamId}` | Select a team for future periods | Admin |
| `DELETE /scoreboards/{scoreboardId}/teams/{teamId}` | Exclude a team from future periods | Admin |
| `POST /scoreboards/{scoreboardId}/periods` | Create a scheduled period | Admin |
| `GET /scoreboards/{scoreboardId}/periods` | List current and archived periods | Authorized viewer |
| `GET /scoreboards/{scoreboardId}/periods/{periodId}` | Read period details and final state | Authorized viewer |
| `POST /scoreboards/{scoreboardId}/periods/{periodId}/activate` | Activate a scheduled period now | Admin |
| `POST /scoreboards/{scoreboardId}/periods/{periodId}/close` | Close an active period now | Admin |
| `POST /scoreboards/{scoreboardId}/periods/{periodId}/reopen` | Reopen a closed period when no other is active | Admin |
| `PUT /scoreboards/{scoreboardId}/periods/{periodId}/teams/{teamId}` | Add a selected team to an active period | Admin |

Creating or activating an overlapping period returns `409 period_overlap`.
Removing a period participant is intentionally not exposed.

## Scoring and standings

| Method and path | Purpose | Access |
| --- | --- | --- |
| `GET /scoreboards/{scoreboardId}/standings` | Read active or selected period standings | Authorized viewer |
| `GET /scoreboards/{scoreboardId}/transactions` | Page through visible transaction history | Authorized viewer |
| `POST /scoreboards/{scoreboardId}/transactions` | Credit or debit a team or member | Admin or assigned Scorer |
| `POST /scoreboards/{scoreboardId}/transactions/{transactionId}/cancellation` | Cancel one transaction with a reason | Authorized actor |

Transaction creation accepts exactly one of `teamId` or `memberId`:

```json
{
  "memberId": "5a949897-ec8b-4378-bbec-0be7af77a5e9",
  "kind": "Credit",
  "amount": 25,
  "reason": "Great presentation"
}
```

Clients should send an `Idempotency-Key` header for transaction creation. A
repeated request by the same actor with the same key returns the original result
and never books points twice. A key reused with different content returns
`409 idempotency_key_reused`.

The standings response includes the period, every participant, shared rank,
exact score, current visual ceiling, and winner state. The server is the only
authority for rank and score calculations.

## Live events

`GET /scoreboards/{scoreboardId}/events` returns an authorized
`text/event-stream`. Event types are:

- `snapshot` with current period, standings, visual ceiling, and recent activity
- `score-created`
- `score-cancelled`
- `period-changed`
- `participation-changed`

Every event has a monotonically ordered event ID. Clients send `Last-Event-ID`
when reconnecting. If the requested event is no longer retained, the server
sends a fresh `snapshot`. Events are hints for presentation; clients replace
their state from the authoritative payload rather than independently adding or
subtracting scores.

## Display configuration

| Method and path | Purpose | Access |
| --- | --- | --- |
| `GET /display/configuration` | Read the current display's allowed boards and preferences | Display session or Admin |
| `PUT /display/configuration` | Select fixed/rotation mode, ordered boards, interval, and sound preference | Display session or Admin |

The server validates every selected board against the display account's
assignments. Browser audio still requires one explicit user interaction before
sound playback begins.

## Stable error codes

The initial contract reserves at least these codes:

- `authentication_required`, `invalid_credentials`, `password_change_required`
- `forbidden`, `resource_not_found`, `validation_failed`
- `active_membership_required`, `team_not_participating`
- `period_not_active`, `period_overlap`, `closed_period`
- `invalid_score_amount`, `score_would_be_negative`
- `transaction_already_cancelled`, `cancellation_not_allowed`
- `idempotency_key_reused`, `concurrent_change`

Errors do not reveal whether an inaccessible resource exists.
