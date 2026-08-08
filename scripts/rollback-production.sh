#!/usr/bin/env bash
set -euo pipefail

env_file="${SCOREBOUND_ENV_FILE:-/etc/scorebound/scorebound.env}"
state_dir="${SCOREBOUND_STATE_DIR:-/var/lib/scorebound/deployments}"
rollback_sha="${SCOREBOUND_ROLLBACK_SHA:-}"
if [[ -z "$rollback_sha" ]] && [[ -f "$state_dir/previous" ]]; then
  rollback_sha="$(<"$state_dir/previous")"
fi
if [[ ! "$rollback_sha" =~ ^[0-9a-f]{40}$ ]]; then
  echo "No valid previous release is available" >&2
  exit 2
fi

release_compose="$state_dir/releases/$rollback_sha/compose.yaml"
if [[ ! -r "$env_file" ]] || [[ ! -r "$release_compose" ]]; then
  echo "Rollback environment or release definition is missing" >&2
  exit 2
fi

compose() {
  SCOREBOUND_IMAGE_TAG="$rollback_sha" docker compose --project-name scorebound \
    --env-file "$env_file" --file "$release_compose" "$@"
}

echo "Rolling Scorebound back to $rollback_sha"
compose up --detach --remove-orphans --no-build

attempt=0
while (( attempt < 60 )); do
  if curl --fail --silent http://127.0.0.1:8080/healthz >/dev/null; then
    old_current=""
    [[ -f "$state_dir/current" ]] && old_current="$(<"$state_dir/current")"
    printf '%s\n' "$rollback_sha" > "$state_dir/current"
    if [[ "$old_current" =~ ^[0-9a-f]{40}$ ]] && [[ "$old_current" != "$rollback_sha" ]]; then
      printf '%s\n' "$old_current" > "$state_dir/previous"
    fi
    echo "Rollback to $rollback_sha is healthy"
    exit 0
  fi
  sleep 2
  ((attempt += 1))
done

compose ps >&2 || true
echo "Rollback release did not become healthy" >&2
exit 1
