#!/usr/bin/env bash
# B-18 test: slack-discord-notify.sh contract test
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_SCRIPT="$SCRIPT_DIR/slack-discord-notify.sh"

test_help() {
  echo "Test: Help flag"
  output=$("$TEST_SCRIPT" --help 2>&1 || true)
  if echo "$output" | grep -q "Usage:"; then
    echo "PASS: Help shows usage"
  else
    echo "FAIL: Expected usage message"
    return 1
  fi
}

test_missing_message() {
  echo "Test: Missing message"
  output=$(SLACK_WEBHOOK_URL="test" "$TEST_SCRIPT" 2>&1 || true)
  if echo "$output" | grep -q "Message required"; then
    echo "PASS: Fails when message missing"
  else
    echo "FAIL: Expected message required error"
    return 1
  fi
}

test_slack_only() {
  echo "Test: Slack only mode"
  output=$(SLACK_WEBHOOK_URL="https://hooks.slack.com/test" "$TEST_SCRIPT" --slack "test message" 2>&1 || true)
  if echo "$output" | grep -q "warning.*DISCORD_WEBHOOK_URL not set"; then
    echo "PASS: Warns about missing Discord webhook"
  else
    echo "FAIL: Expected Discord warning"
    return 1
  fi
}

test_discord_only() {
  echo "Test: Discord only mode"
  output=$(DISCORD_WEBHOOK_URL="https://discord.com/api/webhooks/test" "$TEST_SCRIPT" --discord "test message" 2>&1 || true)
  if echo "$output" | grep -q "warning.*SLACK_WEBHOOK_URL not set"; then
    echo "PASS: Warns about missing Slack webhook"
  else
    echo "FAIL: Expected Slack warning"
    return 1
  fi
}

test_slack_slash_ping() {
  echo "Test: Slack slash command ping"
  output=$(echo '{"command":"/atropos","text":"status"}' | "$TEST_SCRIPT" --slack-slash 2>&1 || true)
  if echo "$output" | grep -q "status"; then
    echo "PASS: Slack slash command responds"
  else
    echo "FAIL: Expected slash command response"
    return 1
  fi
}

test_discord_slash_ping() {
  echo "Test: Discord slash command ping"
  output=$(echo '{"type":1}' | "$TEST_SCRIPT" --discord-slash 2>&1 || true)
  if echo "$output" | grep -q '"type":1'; then
    echo "PASS: Discord slash command responds to ping"
  else
    echo "FAIL: Expected pong response"
    return 1
  fi
}

main() {
  echo "Running slack-discord-notify.sh contract tests..."
  test_help
  test_missing_message
  test_slack_only
  test_discord_only
  test_slack_slash_ping
  test_discord_slash_ping
  echo "All contract tests passed!"
}

main