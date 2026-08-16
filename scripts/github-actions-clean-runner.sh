#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT/.github/workflows/factory-test.yml"

test -s "$WORKFLOW"
grep -Fq 'runs-on: ubuntu-latest' "$WORKFLOW"
grep -Fq 'actions/checkout@v3' "$WORKFLOW"
grep -Fq 'actions/setup-java@v3' "$WORKFLOW"
grep -Fq "java-version: '17'" "$WORKFLOW"
grep -Fq 'distribution: temurin' "$WORKFLOW"
grep -Fq './gradlew clean jar' "$WORKFLOW"

printf '%s\n' GITHUB_ACTIONS_CLEAN_RUNNER_OK
