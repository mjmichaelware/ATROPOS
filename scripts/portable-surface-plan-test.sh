#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLAN="$ROOT/docs/architecture/DOCKER_NATIVE_DESKTOP_ANDROID_WEB_PLAN.md"
BUILD="$ROOT/build.gradle.kts"

test -f "$PLAN"
test -f "$BUILD"

for marker in \
  'src/main/kotlin/atropos/core' \
  'AtroposRepoRootLocator' \
  'Packaging and installation proof' \
  'must not create a second DAG'; do
  grep -Fq "$marker" "$PLAN"
done

grep -Fq 'tasks.register("portableSurfacePlan")' "$BUILD"
grep -Fq 'DOCKER_NATIVE_DESKTOP_ANDROID_WEB_PLAN' "$BUILD" || \
  grep -Fq 'DOCKER_NATIVE_DESKTOP_ANDROID_WEB_PLAN' "$PLAN"

printf '%s\n' \
  'PORTABLE_SURFACE_PLAN_EDGE_OK' \
  'plan_owner=build.gradle.kts' \
  'execution=not_run'
