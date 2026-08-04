#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/atropos-app-nl-routing-proof.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/RoutingProof.kt" <<'KOTLIN'
import atropos.cli.commands.SelfHostNaturalLanguageRouter

fun main() {
    val router = SelfHostNaturalLanguageRouter()
    listOf("calculator", "todo", "notes").forEach { noun ->
        val route = router.route(listOf("build", "a", noun, "app"))
        check(route?.take(2) == listOf("/factory", "run")) { "app request did not route to factory: $noun -> $route" }
    }
    val selfHost = router.route(listOf("build", "yourself"))
    check(selfHost?.take(3) == listOf("/agent", "self-host", "run")) { "self-host request was misrouted: $selfHost" }
    println("APP_FACTORY_NL_ROUTING_PROOF_OK")
}
KOTLIN

OUT="$TMP/nl-routing-proof.jar"
timeout "${ATROPOS_NL_PROOF_TIMEOUT_SECONDS:-120}" kotlinc -include-runtime -d "$OUT" \
  "$TMP/RoutingProof.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppActionRegistry.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppIntent.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectSpec.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectSpecParser.kt" \
  "$ROOT/src/main/kotlin/atropos/cli/commands/SelfHostNaturalLanguageRouter.kt"
timeout "${ATROPOS_NL_PROOF_TIMEOUT_SECONDS:-120}" java -jar "$OUT"
