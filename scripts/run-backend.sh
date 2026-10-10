#!/usr/bin/env bash
# Exports variables from .env (if present) and starts the Spring Boot backend.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
cd "$ROOT/backend"
exec mvn -q spring-boot:run
