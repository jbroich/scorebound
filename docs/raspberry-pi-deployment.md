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

## Upgrade procedure

1. Confirm that the target revision has green GitHub checks.
2. Record the currently deployed commit with `git rev-parse HEAD`.
3. Fetch and switch to the desired release or `main` revision.
4. Build before replacing running containers:

   ```sh
   docker compose build --pull
   docker compose up -d --remove-orphans
   docker compose ps
   ```

5. Check `/healthz`, then confirm login and the wall-display route through the
   public hostname.

Flyway applies database migrations when the API starts. A code rollback across
an incompatible migration requires a database restore and is therefore not an
automatic operation.
