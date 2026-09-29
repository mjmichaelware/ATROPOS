#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# B-17: Linear issue -> territory fix tool
#
# Fetches a Linear issue, extracts the territory path from its labels/body,
# and creates a bounded ATROPOS task to fix it. The tool:
# 1. Reads LINEAR_API_KEY and LINEAR_TEAM_ID from environment
# 2. Fetches the issue by ID
# 3. Parses territory from labels (e.g., "territory:src/core/agent")
# 4. Creates a task in the ATROPOS backlog with the territory bound
# 5. Outputs the task ID for the operator to track

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage: linear-territory-fix.sh <issue-id> [--dry-run]

Fetches a Linear issue and creates an ATROPOS task to fix it within the
declared territory.

Environment:
  LINEAR_API_KEY    - Linear API key (required)
  LINEAR_TEAM_ID    - Linear team ID (required)
  ATROPOS_CONFIG_DIR - ATROPOS config directory (default: ~/.atropos)

Options:
  --dry-run    Show what would be done without creating the task
  --help       Show this help
EOF
}

if [ $# -eq 0 ] || [ "$1" = "--help" ]; then
  usage
  exit 0
fi

ISSUE_ID="$1"
DRY_RUN=false
if [ "${2:-}" = "--dry-run" ]; then
  DRY_RUN=true
fi

# Validate environment
: "${LINEAR_API_KEY:?LINEAR_API_KEY not set}"
: "${LINEAR_TEAM_ID:?LINEAR_TEAM_ID not set}"

CONFIG_DIR="${ATROPOS_CONFIG_DIR:-$HOME/.atropos}"
BACKLOG_FILE="$CONFIG_DIR/backlog.tsv"
TERRITORY_FILE="$CONFIG_DIR/territory.tsv"

# Ensure config dir exists
mkdir -p "$CONFIG_DIR"

# GraphQL query to fetch issue
QUERY='
query GetIssue($id: String!) {
  issue(id: $id) {
    id
    identifier
    title
    description
    labels {
      nodes {
        name
      }
    }
    assignee {
      name
    }
    state {
      name
    }
  }
}
'

# Fetch issue from Linear
fetch_issue() {
  local resp
  resp=$(curl -sS -X POST "https://api.linear.app/graphql" \
    -H "Authorization: $LINEAR_API_KEY" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg id "$ISSUE_ID" --arg query "$QUERY" '{query: $query, variables: {id: $id}}')")

  if echo "$resp" | jq -e '.errors' >/dev/null; then
    echo "error: Linear API error: $(echo "$resp" | jq -r '.errors[0].message')" >&2
    exit 1
  fi

  echo "$resp" | jq '.data.issue'
}

# Extract territory from labels (e.g., "territory:src/core/agent")
extract_territory() {
  local issue_json="$1"
  echo "$issue_json" | jq -r '.labels.nodes[].name' | grep '^territory:' | head -1 | sed 's/^territory://'
}

# Extract priority from labels
extract_priority() {
  local issue_json="$1"
  echo "$issue_json" | jq -r '.labels.nodes[].name' | grep '^priority:' | head -1 | sed 's/^priority://'
}

# Create ATROPOS task
create_task() {
  local territory="$1"
  local title="$2"
  local description="$3"
  local priority="${4:-normal}"
  local issue_id="$5"

  local task_id="task-$(date +%s)-$(printf '%04x' $((RANDOM % 65536)))"
  local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  # Ensure backlog file exists with header
  if [ ! -f "$BACKLOG_FILE" ]; then
    printf 'id\ttitle\tdescription\tterritory\tpriority\tstatus\tcreated_at\tlinear_issue\n' > "$BACKLOG_FILE"
  fi

  # Append task to backlog
  printf '%s\t%s\t%s\t%s\t%s\tpending\t%s\t%s\n' \
    "$task_id" \
    "$title" \
    "$description" \
    "$territory" \
    "$priority" \
    "$timestamp" \
    "$issue_id" >> "$BACKLOG_FILE"

  # Record territory if not exists
  if [ ! -f "$TERRITORY_FILE" ]; then
    printf 'path\towner\trecursive\n' > "$TERRITORY_FILE"
  fi
  if ! grep -q "^$territory\t" "$TERRITORY_FILE" 2>/dev/null; then
    printf '%s\tlinear-import\ttrue\n' "$territory" >> "$TERRITORY_FILE"
  fi

  echo "$task_id"
}

# Main
issue_json=$(fetch_issue)
title=$(echo "$issue_json" | jq -r '.title')
description=$(echo "$issue_json" | jq -r '.description // ""')
state=$(echo "$issue_json" | jq -r '.state.name')

echo "Linear Issue: $title"
echo "State: $state"

# Extract territory from labels
territory=$(extract_territory "$issue_json")
if [ -z "$territory" ] || [ "$territory" = "null" ]; then
  echo "error: No territory label found on issue. Add a label like 'territory:src/core/agent'" >&2
  exit 1
fi
echo "Territory: $territory"

priority=$(extract_priority "$issue_json")
priority=${priority:-normal}
echo "Priority: $priority"

# Validate territory exists in repo
if [ ! -d "$REPO_ROOT/$territory" ] && [ ! -f "$REPO_ROOT/$territory" ]; then
  echo "warning: Territory path '$territory' does not exist in repository" >&2
fi

if [ "$DRY_RUN" = true ]; then
  echo "DRY RUN: Would create task for territory '$territory' with title '$title'"
  exit 0
fi

task_id=$(create_task "$territory" "$title" "$description" "$priority" "$ISSUE_ID")
echo "Created task: $task_id"
echo "Run 'atropos autonomous backlog' to process the backlog"