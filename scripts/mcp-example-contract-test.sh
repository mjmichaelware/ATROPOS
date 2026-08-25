#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 - "$repo_root" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1]) / "docs" / "mcp-examples"
# optional-catalog.json is intentionally a single config fixture, not a brand adapter farm.
required = {
    "github", "filesystem", "playwright", "markitdown", "chrome-devtools",
    "fetch", "sqlite", "postgres", "docker", "code-intelligence", "npm-intelligence",
    "cloud-deploy", "browser-use", "n8n", "gitlab", "bitbucket", "linear", "jira",
    "confluence", "puppeteer", "redis", "snyk", "sonar", "datadog", "newrelic",
    "supabase", "firebase", "slack", "discord", "teams", "asana", "clickup", "notion",
    "pagerduty", "opsgenie", "terraform", "pulumi", "k8s", "aws-read", "gcp-read",
    "azure-read", "sequential-thinking",
}
seen = set()
for path in sorted(root.glob("*.json")):
    document = json.loads(path.read_text())
    servers = document.get("servers")
    if not isinstance(servers, list) or not servers:
        raise SystemExit(f"MCP_EXAMPLE_CONTRACT_FAIL no servers[]: {path.name}")
    for server in servers:
        name = server.get("name")
        if not name or server.get("enabled") is not False:
            raise SystemExit(f"MCP_EXAMPLE_CONTRACT_FAIL enabled/unnamed server: {path.name}")
        if server.get("community") is not True:
            raise SystemExit(f"MCP_EXAMPLE_CONTRACT_FAIL missing community marker: {path.name}")
        seen.add(name)
missing = required - seen
if missing:
    raise SystemExit(f"MCP_EXAMPLE_CONTRACT_FAIL missing={sorted(missing)}")
print(f"MCP_EXAMPLE_CONTRACT_OK files={len(list(root.glob('*.json')))} servers={len(seen)}")
PY
