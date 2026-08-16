#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build.gradle.kts"
WRAPPER="$ROOT/gradlew"
PROPERTIES="$ROOT/gradle/wrapper/gradle-wrapper.properties"

test -f "$BUILD"
test -f "$WRAPPER"
test -f "$PROPERTIES"

# These are source/configuration contracts. The script deliberately does not
# invoke Gradle or Kotlin; the executable build gate remains a later phase.
grep -Fq 'org.jetbrains.kotlin.jvm' "$BUILD"
grep -Fq 'KotlinCompile' "$BUILD"
grep -Fq 'kotlinCompatScan' "$BUILD"
grep -Fq 'gradle-' "$PROPERTIES"

# Git state is inspected only when this focused contract is explicitly run.
git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null

printf '%s\n' \
  'KOTLIN_JVM_RUNTIME_CONTRACT_OK' \
  'LOCAL_TOOLCHAIN_PROVIDER_CONTRACT_OK' \
  'KOTLIN_COMPILE_PROBE_CONTRACT_OK' \
  'GIT_STATE_PROBE_CONTRACT_OK' \
  'execution=not_run'
