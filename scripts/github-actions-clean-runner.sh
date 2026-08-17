#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT/.github/workflows/factory-test.yml"

test -s "$WORKFLOW"
# Quoting is YAML's business, not this contract's.
#
# These were fixed-string greps, and the workflow writes `distribution:
# 'temurin'` in flow style. `grep -F 'distribution: temurin'` cannot match a
# quoted scalar, so this check failed on every run -- including Factory CI's
# own, which calls it as its first step. A guard that always fails guards
# nothing; it just makes red the normal colour, and then a real break is
# invisible.
grep -Eq 'runs-on: ubuntu-latest' "$WORKFLOW"
grep -Eq 'actions/checkout@v[0-9]+' "$WORKFLOW"
grep -Eq 'actions/setup-java@v[0-9]+' "$WORKFLOW"
grep -Eq "java-version: '?17'?" "$WORKFLOW"
grep -Eq "distribution: '?temurin'?" "$WORKFLOW"
grep -Eq './gradlew clean jar' "$WORKFLOW"

printf '%s\n' GITHUB_ACTIONS_CLEAN_RUNNER_OK
