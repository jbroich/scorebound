#!/usr/bin/env bash
set -euo pipefail

release_sha="${SCOREBOUND_RELEASE_SHA:?SCOREBOUND_RELEASE_SHA is required}"
env_file="${SCOREBOUND_ENV_FILE:-/etc/scorebound/scorebound.env}"
state_dir="${SCOREBOUND_STATE_DIR:-/var/lib/scorebound/deployments}"

if [[ ! "$release_sha" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Release identifier must be a full Git commit SHA" >&2
  exit 2
fi
checked_out_sha="$(git rev-parse HEAD)"
if [[ "$checked_out_sha" != "$release_sha" ]]; then
  echo "Checked-out revision does not match the verified CI revision" >&2
  exit 2
fi
if [[ ! -r "$env_file" ]]; then
  echo "Runtime environment file is missing or unreadable: $env_file" >&2
  exit 2
fi

mkdir -p "$state_dir/releases/$release_sha"
previous_sha=""
if [[ -f "$state_dir/current" ]]; then
  previous_sha="$(<"$state_dir/current")"
fi

compose() {
  SCOREBOUND_IMAGE_TAG="$release_sha" docker compose \
    --project-name scorebound --env-file "$env_file" "$@"
}

wait_for_health() {
  local attempt=0 service container status healthy
  while (( attempt < 60 )); do
    healthy=true
    for service in database api web; do
      container="$(compose ps --quiet "$service")"
      if [[ -z "$container" ]]; then
        healthy=false
        break
      fi
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container")"
      if [[ "$status" != "healthy" ]]; then
        healthy=false
        break
      fi
    done
    if [[ "$healthy" == true ]] && curl --fail --silent --show-error \
      http://127.0.0.1:8080/healthz >/dev/null; then
      return 0
    fi
    sleep 2
    ((attempt += 1))
  done
  return 1
}

echo "Building Scorebound release $release_sha"
compose config --quiet
compose build --pull
cp compose.yaml "$state_dir/releases/$release_sha/compose.yaml"

echo "Starting Scorebound release $release_sha"
compose up --detach --remove-orphans
if ! wait_for_health; then
  echo "Release $release_sha did not become healthy" >&2
  compose ps >&2 || true
  if [[ "$previous_sha" =~ ^[0-9a-f]{40}$ ]] \
      && [[ -f "$state_dir/releases/$previous_sha/compose.yaml" ]]; then
    echo "Restoring previous release $previous_sha"
    SCOREBOUND_ROLLBACK_SHA="$previous_sha" bash scripts/rollback-production.sh
  fi
  exit 1
fi

if [[ "$previous_sha" =~ ^[0-9a-f]{40}$ ]] && [[ "$previous_sha" != "$release_sha" ]]; then
  printf '%s\n' "$previous_sha" > "$state_dir/previous"
fi
printf '%s\n' "$release_sha" > "$state_dir/current"
echo "Scorebound release $release_sha is healthy"
