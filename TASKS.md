# Scorebound backlog

This file mirrors the ordered GitHub backlog so work can continue before the
remote repository is available. GitHub Issues are the source of truth once they
have been created.

## MVP milestone

1. [x] `chore: establish a reproducible project baseline`
   - Make backend tests independent from a manually prepared database.
   - Verify frontend linting and production builds.
   - Document the supported local development workflow.
2. [ ] `ci: validate pull requests and main`
   - Run backend tests and frontend lint/build in GitHub Actions.
   - Cache dependencies and publish useful failure output.
3. [ ] `design: define the domain model and API contract`
   - Specify accounts, roles, teams, members, memberships, scoreboards,
     periods, participation, and immutable score transactions.
   - Record lifecycle rules, authorization, and validation errors.
4. [x] `feat: implement authentication and role-based access`
   - Support `Admin`, `Scorer`, `Member`, and `Display` roles.
   - Bootstrap the first admin and support temporary passwords.
5. [ ] `feat: manage teams and members`
   - Manage team presentation data and member status.
   - Preserve membership history with one active team per member.
6. [ ] `feat: manage scoreboards and competition periods`
   - Select global teams per scoreboard.
   - Enforce one active period and archive completed standings.
7. [ ] `feat: record and cancel score transactions`
   - Credit or debit a team or member with a required reason.
   - Preserve an auditable, visible cancellation trail.
8. [ ] `feat: build the responsive management interface`
   - Optimize score entry for desktop and smartphone use.
   - Provide accessible administration for accounts, teams, and scoreboards.
9. [ ] `feat: publish score changes in real time`
   - Update connected displays without page reloads.
   - Recover cleanly after temporary connection loss.
10. [ ] `feat: create the animated wall display`
    - Optimize the layout for a landscape Galaxy Tab S6 Lite.
    - Render adaptive glass containers with proportional colored balls.
    - Support transaction and winner animations, optional sounds, and display
      rotation.
11. [x] `feat: localize and theme the web interface`
    - Use English by default and provide German translations.
    - Support system light/dark mode and the dedicated dark display theme.
12. [ ] `infra: package Scorebound for ARM64 with Docker Compose`
    - Expose the web app through one local HTTP port for Cloudflare Tunnel.
    - Keep configuration and secrets outside the images.
13. [ ] `cd: deploy main to the Raspberry Pi`
    - Deploy only after successful CI through a self-hosted GitHub runner.
    - Provide rollback and deployment health checks.
14. [ ] `test: cover the MVP user journeys`
    - Cover permissions, scoring invariants, period lifecycle, responsive score
      entry, and display updates.
    - Verify keyboard operation and reduced-motion behavior.

## Later issues

- [ ] `feat: add configurable categories and tags to score transactions`
- [ ] `feat: make Scorebound an installable PWA`
- [ ] `feat: add email-based password recovery`
- [ ] `feat: export standings and transaction history`
- [ ] `infra: automate encrypted database backups`

## Workflow

1. Refine the highest ready issue and its acceptance criteria.
2. Create a feature branch linked to the issue.
3. Implement the smallest complete change.
4. Run affected tests, linting, and builds.
5. Open a pull request and merge only after CI and review.
