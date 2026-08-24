#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
client="$repo_root/src/main/kotlin/atropos/core/github/GitHubApiClient.kt"
handler="$repo_root/src/main/kotlin/atropos/cli/GitHubCommandHandler.kt"
test_file="$repo_root/src/test/kotlin/atropos/core/github/GitHubApiClientTest.kt"

for path in "$client" "$handler" "$test_file"; do
  test -f "$path" || { echo "missing GitHub write contract path: $path" >&2; exit 1; }
done

grep -q 'data class GitHubWriteAuthorization' "$client"
grep -q 'if (method != "GET") requireNotNull(request.authorization)' "$client"
grep -q 'writes_require_operator_confirmation_before_secret_or_transport' "$test_file"

for operation in create-issue comment-issue create-pr comment-pr request-review create-check update-check; do
  grep -q "\"$operation\"" "$handler" || {
    echo "missing GitHub write command: $operation" >&2
    exit 1
  }
done

grep -q 'write requires explicit --confirm <id>' "$handler"
grep -q 'tokens.indexOf("--confirm")' "$handler"

echo "GITHUB_WRITE_CONTRACT_OK operations=7"
