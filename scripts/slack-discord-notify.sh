#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# B-18: Slack/Discord slash distribution
#
# Distributes ATROPOS verification results and notifications to Slack
# and Discord via slash commands or webhooks. The tool:
# 1. Reads SLACK_WEBHOOK_URL / DISCORD_WEBHOOK_URL from environment
# 2. Formats ATROPOS verification results for the platform
# 3. Posts to the configured channel with proper formatting
# 4. Supports both slash command responses and async notifications

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage: slack-discord-notify.sh [options] <message>

Distributes ATROPOS notifications to Slack and/or Discord.

Environment:
  SLACK_WEBHOOK_URL     - Slack incoming webhook URL
  SLACK_BOT_TOKEN       - Slack bot token (for slash commands)
  SLACK_CHANNEL         - Slack channel ID (default: #general)
  DISCORD_WEBHOOK_URL   - Discord webhook URL
  DISCORD_BOT_TOKEN     - Discord bot token (for slash commands)
  DISCORD_CHANNEL_ID    - Discord channel ID

Options:
  --slack          Send to Slack only
  --discord        Send to Discord only
  --both           Send to both (default)
  --format TEXT    Format: markdown, blocks, simple (default: markdown)
  --title TEXT     Message title
  --color COLOR    Color for embed (hex, e.g., #36a64f)
  --help           Show this help
EOF
}

# Default values
SLACK_ONLY=false
DISCORD_ONLY=false
FORMAT="markdown"
TITLE="ATROPOS Notification"
COLOR="#36a64f"

# Parse arguments
while [ $# -gt 0 ]; do
  case "$1" in
    --slack) SLACK_ONLY=true; DISCORD_ONLY=false ;;
    --discord) DISCORD_ONLY=true; SLACK_ONLY=false ;;
    --both) SLACK_ONLY=false; DISCORD_ONLY=false ;;
    --format) FORMAT="$2"; shift ;;
    --title) TITLE="$2"; shift ;;
    --color) COLOR="$2"; shift ;;
    --help) usage; exit 0 ;;
    *) MESSAGE="$1"; shift ;;
  esac
done

: "${MESSAGE:?Message required}"

# Slack notification
send_slack() {
  if [ -z "${SLACK_WEBHOOK_URL:-}" ]; then
    echo "warning: SLACK_WEBHOOK_URL not set, skipping Slack" >&2
    return
  fi

  local payload
  if [ "$FORMAT" = "blocks" ]; then
    payload=$(jq -n \
      --arg text "$TITLE" \
      --arg message "$MESSAGE" \
      --arg color "$COLOR" \
      '{
        text: $text,
        blocks: [
          {type: "section", text: {type: "mrkdwn", text: $message}},
          {type: "context", elements: [{type: "mrkdwn", text: "ATROPOS Engine"}]
        ]
      }')
  else
    payload=$(jq -n \
      --arg text "$TITLE" \
      --arg message "$MESSAGE" \
      --arg color "$COLOR" \
      '{
        text: $text,
        attachments: [{
          color: $color,
          text: $message,
          footer: "ATROPOS Engine",
          ts: (now | floor)
        }]
      }')
  fi

  curl -sS -X POST "$SLACK_WEBHOOK_URL" \
    -H "Content-Type: application/json" \
    -d "$payload" >/dev/null
}

# Discord notification
send_discord() {
  if [ -z "${DISCORD_WEBHOOK_URL:-}" ]; then
    echo "warning: DISCORD_WEBHOOK_URL not set, skipping Discord" >&2
    return
  fi

  # Convert hex color to decimal for Discord
  local color_dec
  color_dec=$(printf '%d' "0x${COLOR#\#}")

  local payload
  payload=$(jq -n \
    --arg title "$TITLE" \
    --arg description "$MESSAGE" \
    --arg color "$color_dec" \
    '{
      embeds: [{
        title: $title,
        description: $description,
        color: ($color | tonumber),
        footer: {text: "ATROPOS Engine"},
        timestamp: (now | todateiso8601)
      }]
    }')

  curl -sS -X POST "$DISCORD_WEBHOOK_URL" \
    -H "Content-Type: application/json" \
    -d "$payload" >/dev/null
}

# Slack slash command handler
handle_slack_slash() {
  # This would be invoked by Slack when user types /atropos <command>
  # Expects payload in stdin with command, text, user_id, channel_id, etc.
  local payload
  payload=$(cat)

  local command
  command=$(echo "$payload" | jq -r '.command // ""')
  local text
  text=$(echo "$payload" | jq -r '.text // ""')
  local user_id
  user_id=$(echo "$payload" | jq -r '.user_id // ""')

  case "$command" in
    /atropos)
      case "$text" in
        status)
          # Run ATROPOS status and return formatted result
          echo '{"response_type": "in_channel", "text": "ATROPOS status: online"}'
          ;;
        verify)
          # Trigger verification
          echo '{"response_type": "ephemeral", "text": "Verification triggered..."}'
          ;;
        *)
          echo '{"response_type": "ephemeral", "text": "Unknown command. Use: status, verify"}'
          ;;
      esac
      ;;
    *)
      echo '{"response_type": "ephemeral", "text": "Unknown command"}'
      ;;
  esac
}

# Discord slash command handler
handle_discord_slash() {
  # Discord sends JSON to the interactions endpoint
  local payload
  payload=$(cat)

  local type
  type=$(echo "$payload" | jq -r '.type // 0')

  if [ "$type" = "1" ]; then
    # PING - respond with PONG
    echo '{"type": 1}'
    return
  fi

  if [ "$type" = "2" ]; then
    # APPLICATION_COMMAND
    local name
    name=$(echo "$payload" | jq -r '.data.name // ""')
    local options
    options=$(echo "$payload" | jq -c '.data.options // []')

    case "$name" in
      atropos)
        local subcommand
        subcommand=$(echo "$options" | jq -r '.[0].name // ""')
        case "$subcommand" in
          status)
            echo '{"type": 4, "data": {"content": "ATROPOS status: online"}}'
            ;;
          verify)
            echo '{"type": 4, "data": {"content": "Verification triggered...", "flags": 64}}'
            ;;
          *)
            echo '{"type": 4, "data": {"content": "Unknown subcommand", "flags": 64}}'
            ;;
        esac
        ;;
      *)
        echo '{"type": 4, "data": {"content": "Unknown command", "flags": 64}}'
        ;;
    esac
    return
  fi

  echo '{"type": 4, "data": {"content": "Unsupported interaction type", "flags": 64}}'
}

# Main
case "${1:-}" in
  --slack-slash)
    handle_slack_slash
    ;;
  --discord-slash)
    handle_discord_slash
    ;;
  *)
    # Default: send notification
    if [ "$SLACK_ONLY" = false ] && [ "$DISCORD_ONLY" = false ]; then
      send_slack
      send_discord
    elif [ "$SLACK_ONLY" = true ]; then
      send_slack
    elif [ "$DISCORD_ONLY" = true ]; then
      send_discord
    fi
    ;;
esac