#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCANNER="$SCRIPT_ROOT/scripts/kotlin-compat-scan.sh"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/atropos-kotlin-compat-test.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/src/main/kotlin"
cat > "$TMP/src/main/kotlin/Portable.kt" <<'KOTLIN'
package fixture

fun portable() = "ok"
KOTLIN
cat > "$TMP/build.gradle.kts" <<'GRADLE'
plugins { kotlin("jvm") version "1.9.24" }
dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test-junit"))
}
GRADLE

ATROPOS_KOTLIN_COMPAT_SCAN_ROOT="$TMP" bash "$SCANNER" >/dev/null

printf '%s\n' 'import sun.misc.Unsafe' >> "$TMP/src/main/kotlin/Portable.kt"
if ATROPOS_KOTLIN_COMPAT_SCAN_ROOT="$TMP" bash "$SCANNER" >/dev/null 2>&1; then
    echo 'KOTLIN_COMPAT_SCAN_EDGE_FAIL forbidden import was accepted' >&2
    exit 1
fi

cat > "$TMP/src/main/kotlin/Portable.kt" <<'KOTLIN'
package fixture

import kotlin.io.path.Path

fun portable() = Path("fixture")
KOTLIN
if ATROPOS_KOTLIN_COMPAT_SCAN_ROOT="$TMP" bash "$SCANNER" >/dev/null 2>&1; then
    echo 'KOTLIN_COMPAT_SCAN_EDGE_FAIL kotlin.io.path import was accepted' >&2
    exit 1
fi

cat > "$TMP/src/main/kotlin/Portable.kt" <<'KOTLIN'
package fixture

fun portable() = Class.forName("fixture.Unsafe")
KOTLIN
if ATROPOS_KOTLIN_COMPAT_SCAN_ROOT="$TMP" bash "$SCANNER" >/dev/null 2>&1; then
    echo 'KOTLIN_COMPAT_SCAN_EDGE_FAIL reflection call was accepted' >&2
    exit 1
fi

cat > "$TMP/src/main/kotlin/Portable.kt" <<'KOTLIN'
package fixture

fun portable() = "/home/fixture"
KOTLIN
if ATROPOS_KOTLIN_COMPAT_SCAN_ROOT="$TMP" bash "$SCANNER" >/dev/null 2>&1; then
    echo 'KOTLIN_COMPAT_SCAN_EDGE_FAIL absolute host path was accepted' >&2
    exit 1
fi

cat > "$TMP/src/main/kotlin/Portable.kt" <<'KOTLIN'
package fixture

fun portable() = "ok"
KOTLIN
cat > "$TMP/build.gradle.kts" <<'GRADLE'
plugins { kotlin("jvm") version "1.9.24" }
dependencies {
    implementation("example.invalid:unsupported-runtime:1.0")
}
GRADLE
if ATROPOS_KOTLIN_COMPAT_SCAN_ROOT="$TMP" bash "$SCANNER" >/dev/null 2>&1; then
    echo 'KOTLIN_COMPAT_SCAN_EDGE_FAIL unsupported dependency was accepted' >&2
    exit 1
fi

printf '%s\n' \
    'KOTLIN_COMPAT_SCAN_EDGE_OK' \
    'portable_fixture_passed=true' \
    'forbidden_import_fixture_refused=true' \
    'kotlin_io_path_fixture_refused=true' \
    'reflection_call_fixture_refused=true' \
    'absolute_host_path_fixture_refused=true' \
    'unsupported_dependency_fixture_refused=true' \
    'build_execution=not_run'
