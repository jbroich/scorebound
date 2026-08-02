# Scorebound MVP product requirements

## Product intent

Scorebound is a playful, self-hosted web application for apprentice teams. It
turns arbitrary achievements, helpful actions, exam results, jokes, and other
office events into an ongoing team competition. The primary experience is a
permanent wall display, while scoring and administration also work well on
smartphones and desktop computers.

The application must remain generic. Apprentices are the first audience and
training years are the first team structure, but neither concept is hard-coded.

## Core concepts

### Accounts and roles

- Accounts and members are separate and may optionally be linked.
- An account can have multiple roles: `Admin`, `Scorer`, `Member`, and
  `Display`.
- `Admin` manages accounts, teams, members, scoreboards, and periods.
- `Scorer` can be assigned to specific scoreboards and records points there.
- `Member` can view scoreboards in which their active team participates.
- `Display` can only access its assigned wall displays.
- There is no public registration and no anonymous application access.
- The first admin is bootstrapped with environment-provided credentials and
  must replace the temporary password on first login.
- Admins create accounts and issue temporary passwords for password resets.
- Normal sessions last up to 12 hours; display sessions can last up to 30 days.

### Teams and members

- Teams are global and can participate in multiple scoreboards.
- A team has a name, short name, color, and optional image.
- A member has a display name, optional first and last names, and an
  active/inactive status.
- A member has exactly one active team. Membership changes are historized.
- A member may change teams during an active competition period.

### Scoreboards and periods

- One installation supports multiple independent scoreboards.
- A scoreboard selects its participating global teams.
- Each scoreboard has at most one active competition period.
- A period has a name, start date, end date, and installation timezone.
- The default timezone is `Europe/Berlin` and is configurable.
- Every period starts all participating teams at zero.
- A period closes automatically at its end and can also be closed by an admin.
- Closed periods and their final standings remain archived.
- Closed periods are immutable unless an admin explicitly reopens them.
- Teams can join an active period. Existing participation is retained for
  history and can only be disabled for future periods.

### Score transactions

- A scorer can target either a participating team or a member whose active team
  participates in the selected scoreboard.
- A transaction credits or debits an integer amount from 1 through 1000.
- A debit must never reduce a team's period score below zero.
- Every transaction requires a free-text reason and records its actor and time.
- Member transactions store the member and team at booking time, so later team
  changes never rewrite history.
- Transactions take effect immediately without an approval step.
- A scorer may cancel their own transactions; an admin may cancel any
  transaction.
- Cancellation requires a reason. Cancelled transactions remain visible with
  strikethrough styling and no longer affect standings.
- Quick-entry amounts are 10, 25, 50, and 100, for both credits and debits.

### Standings

- Every participating team is shown, including teams with zero points.
- Teams with equal scores share the same rank; there is no artificial
  tie-breaker.
- If a completed period has several teams tied for first, all are winners.

## User experience

- English is the default interface language and German is selectable.
- Administration is responsive, keyboard accessible, and optimized for quick
  smartphone scoring.
- Administration follows the system light/dark preference.
- The wall display targets a permanently mounted Galaxy Tab S6 Lite in
  landscape orientation and remains responsive for other 16:9 screens.
- The display uses an original dark visual design with one colored glass
  container per team and a proportional field of animated balls.
- Exact scores and ranks remain clearly readable beside the visualization.
- The visual maximum is calculated above the current leader with headroom,
  rounded to a stable useful value, and never decreases during a period.
- New transactions arrive without a page reload. Credits and debits trigger
  short distinct animations and optionally distinct sounds.
- Browser audio is enabled once interactively and remembered for the display.
- A display can show one fixed scoreboard or rotate through selected boards.
- Personal transactions show the member display name in the activity feed.
- Period completion triggers a winner animation, including shared winners.
- Reduced-motion preferences must be respected.

## Delivery and operations

- The public source repository is hosted on GitHub.
- Issues, pull requests, and commits are written in English by default.
- Work uses feature branches and pull requests. `main` requires successful CI
  and at least one approval.
- Pull requests run backend tests plus frontend linting and production builds.
- Merges to `main` deploy automatically to a Raspberry Pi through a self-hosted
  GitHub Actions runner.
- Production uses ARM64-compatible Docker Compose and PostgreSQL.
- One local HTTP endpoint serves the web application and API. The existing
  Cloudflare Tunnel publishes it as `scorebound.joelbroich.de`.
- There is initially no staging environment.

## Explicitly deferred

- Configurable score categories and tags
- Installable PWA behavior
- Email-based password recovery
- CSV or other data exports
- Automated database backups
