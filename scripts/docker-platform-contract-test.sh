#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCKERFILE="$ROOT/Dockerfile"
test -s "$DOCKERFILE"
grep -Fq 'AS jvm' "$DOCKERFILE"
grep -Fq 'AS native' "$DOCKERFILE"
grep -Fq 'native-image --no-fallback' "$DOCKERFILE"
grep -Fq 'HEALTHCHECK' "$DOCKERFILE"
grep -Fq 'STOPSIGNAL SIGTERM' "$DOCKERFILE"
printf '%s\n' DOCKER_PLATFORM_CONTRACT_OK
