# Raspberry Pi deployment

The production Compose stack builds native multi-architecture images from
ARM64-capable base images. On a 64-bit Raspberry Pi it builds and runs as
`linux/arm64` without an architecture override. It exposes only the Nginx web
entry point on `127.0.0.1:8080`; the API and PostgreSQL remain on an internal
Docker network.

## First installation

1. Install a 64-bit Raspberry Pi OS, Docker Engine, and the Docker Compose
   plugin.
2. Clone the repository and copy `.env.example` to `.env`.
3. Replace both example passwords. Keep `.env` readable only by the deployment
   user and never commit it.
4. Build and start the stack:

   ```sh
   docker compose build --pull
   docker compose up -d
   docker compose ps
   ```

5. Wait until all three services report `healthy`, then verify the entry point:

   ```sh
   curl --fail http://127.0.0.1:8080/healthz
   ```

6. Log in with the bootstrap account and replace its temporary password. Then
   remove `SCOREBOUND_BOOTSTRAP_ADMIN_USERNAME` and
   `SCOREBOUND_BOOTSTRAP_ADMIN_PASSWORD` from `.env` and run
   `docker compose up -d` once more.

The named `postgres-data` volume stores the database independently of container
replacement. Automated backups remain a separate backlog item; do not treat a
Docker volume as a backup.

## Cloudflare Tunnel

Configure the existing tunnel's public hostname as
`scorebound.joelbroich.de` and its service as `http://localhost:8080`. Keep the
Compose port bound to `127.0.0.1` unless another trusted local reverse proxy
needs access. Browser traffic remains HTTPS through Cloudflare while the local
tunnel-to-container hop stays private on the Pi.

## Automated upgrades

Production upgrades run through `.github/workflows/deploy-production.yml`. The
workflow starts only when the `CI` workflow for `main` completes successfully.
It checks out that exact commit SHA on a dedicated ARM64 self-hosted runner,
builds both images with the immutable SHA tag, replaces the Compose services,
and waits for the database, API, web container, and local HTTP entry point to
become healthy.

## Self-hosted runner setup

1. Create a dedicated unprivileged Linux account on the Raspberry Pi and add it
   to the Docker group. Install `git`, `curl`, Docker Engine, and the Compose
   plugin.
2. Register a repository-level GitHub Actions runner with the labels
   `self-hosted`, `linux`, `ARM64`, and `scorebound-production`. Do not reuse it
   for pull-request jobs or other repositories.
3. Store the populated runtime environment outside the checkout:

   ```sh
   sudo install -d -m 0750 -o scorebound -g scorebound /etc/scorebound
   sudo install -m 0600 -o scorebound -g scorebound .env /etc/scorebound/scorebound.env
   sudo install -d -m 0750 -o scorebound -g scorebound /var/lib/scorebound/deployments
   ```

4. Create a protected GitHub environment named `production`. The workflow does
   not need repository secrets because database and bootstrap credentials stay
   solely in `/etc/scorebound/scorebound.env` on the Pi.
5. Start the runner as a system service. The next successful `main` CI run will
   deploy automatically; no staging environment is involved.

The deployment retains commit-tagged images and a copy of each release's
Compose definition. It never prints the runtime environment file.

## Manual upgrade fallback

1. Confirm that the target revision has green GitHub checks.
2. Record the currently deployed commit with `git rev-parse HEAD`.
3. Fetch and switch to the desired `main` revision.
4. Build before replacing running containers:

   ```sh
   docker compose build --pull
   docker compose up -d --remove-orphans
   docker compose ps
   ```

5. Check `/healthz`, then confirm login and the wall-display route through the
   public hostname.

## Rollback

If a new deployment does not become healthy, the deployment script
automatically attempts to restore the previously recorded image tag. To trigger
the same rollback manually as the runner account:

```sh
cd /path/to/the/scorebound/runner/checkout
SCOREBOUND_ENV_FILE=/etc/scorebound/scorebound.env \
SCOREBOUND_STATE_DIR=/var/lib/scorebound/deployments \
bash scripts/rollback-production.sh
```

The rollback uses `--no-build`, verifies `/healthz`, and swaps the recorded
current/previous releases only after health succeeds. Flyway applies database
migrations when the API starts; the automated rollback deliberately does not
modify or restore the PostgreSQL volume. A code rollback across an incompatible
migration therefore requires a separate database restore.
