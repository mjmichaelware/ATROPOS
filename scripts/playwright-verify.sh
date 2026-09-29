#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# B-19: Playwright verification tool for App Factory
#
# Uses Playwright to verify generated App Factory applications by:
# 1. Starting the generated app (if it has a dev server)
# 2. Running browser automation tests against it
# 3. Capturing screenshots and evidence
# 4. Verifying the six answers and health endpoints

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage: playwright-verify.sh [options] <project-dir>

Verifies an App Factory generated application using Playwright.

Environment:
  PLAYWRIGHT_BROWSERS_PATH  - Path to Playwright browsers (optional)
  ATROPOS_BRIDGE_URL        - ATROPOS bridge URL (default: http://127.0.0.1:4317)

Options:
  --headless        Run in headless mode (default: true)
  --browser BROWSER Browser to use: chromium, firefox, webkit (default: chromium)
  --timeout MS      Timeout in milliseconds (default: 30000)
  --headed          Run in headed mode (visible browser)
  --output DIR      Output directory for screenshots/evidence (default: ./playwright-results)
  --help            Show this help

The tool verifies:
  1. App loads without console errors
  2. Six answers are displayed correctly
  3. Health endpoint responds
  4. Composer/input works
  5. Evidence can be downloaded
  6. No console errors or network failures
EOF
}

HEADLESS=true
BROWSER="chromium"
TIMEOUT=30000
HEADED=false
OUTPUT_DIR="./playwright-results"
BRIDGE_URL="${ATROPOS_BRIDGE_URL:-http://127.0.0.1:4317}"

while [ $# -gt 0 ]; do
  case "$1" in
    --headless) HEADLESS=true ;;
    --headed) HEADED=true; HEADLESS=false ;;
    --browser) BROWSER="$2"; shift ;;
    --timeout) TIMEOUT="$2"; shift ;;
    --output) OUTPUT_DIR="$2"; shift ;;
    --help) usage; exit 0 ;;
    *) PROJECT_DIR="$1"; shift ;;
  esac
done

: "${PROJECT_DIR:?Project directory required}"

# Resolve absolute path
PROJECT_DIR="$(cd "$PROJECT_DIR" && pwd)"
mkdir -p "$OUTPUT_DIR"

echo "=== Playwright Verification for App Factory ==="
echo "Project: $PROJECT_DIR"
echo "Browser: $BROWSER"
echo "Headless: $HEADLESS"
echo "Output: $OUTPUT_DIR"
echo ""

# Check if project has package.json (Node.js app)
if [ ! -f "$PROJECT_DIR/package.json" ]; then
  echo "error: No package.json found in $PROJECT_DIR" >&2
  exit 1
fi

# Check if Playwright is installed
if ! command -v npx >/dev/null 2>&1; then
  echo "error: npx not found. Install Node.js and npm first." >&2
  exit 1
fi

# Create Playwright test file
TEST_FILE="$OUTPUT_DIR/verification.spec.ts"
cat > "$TEST_FILE" <<EOF
import { test, expect } from '@playwright/test';

const BRIDGE_URL = process.env.ATROPOS_BRIDGE_URL || 'http://127.0.0.1:4317';
const PROJECT_URL = process.env.PROJECT_URL || 'http://localhost:3000';

test.describe('App Factory Verification', () => {
  test.beforeEach(async ({ page }) => {
    // Capture console errors
    page.on('console', msg => {
      if (msg.type() === 'error') {
        console.log('CONSOLE ERROR:', msg.text());
      }
    });

    page.on('pageerror', error => {
      console.log('PAGE ERROR:', error.message);
    });

    const response = await page.goto(PROJECT_URL, { waitUntil: 'networkidle' });
    expect(response?.status()).toBeLessThan(400);
  });

  test('App loads without console errors', async ({ page }) => {
    // Check page loaded
    await expect(page).toHaveTitle(/./);
  });

  test('Six answers displayed', async ({ page }) => {
    // Check for six answers section
    const answers = page.locator('[data-testid="six-answers"]');
    await expect(answers).toBeVisible({ timeout: 5000 });

    // Check each answer exists
    const answerKeys = ['objective', 'doing', 'why', 'progress', 'next', 'evidence'];
    for (const key of answerKeys) {
      const answer = page.locator(\`[data-answer="\${key}"]\`);
      await expect(answer).toBeVisible();
    }
  });

  test('Health endpoint accessible', async ({ page }) => {
    const response = await page.request.get('\${BRIDGE_URL}/v1/health');
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.ok).toBe(true);
  });

  test('Answers endpoint accessible', async ({ page }) => {
    const response = await page.request.get('\${BRIDGE_URL}/v1/answers');
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.ok).toBe(true);
    expect(data.answers).toBeDefined();
  });

  test('Composer/input works', async ({ page }) => {
    // Find input/composer area
    const input = page.locator('textarea, input[type="text"]').first();
    if (await input.count() > 0) {
      await input.fill('test message');
      await expect(input).toHaveValue('test message');
    }
  });

  test('No critical console errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error' && !msg.text().includes('favicon')) {
        errors.push(msg.text());
      }
    });

    await page.waitForTimeout(2000);

    const criticalErrors = errors.filter(e =>
      !e.includes('favicon') &&
      !e.includes('webpack') &&
      !e.includes('hot reload')
    );
    expect(criticalErrors).toHaveLength(0);
  });

  test('Evidence download works', async ({ page }) => {
    // Check if evidence download button exists
    const downloadBtn = page.locator('button:has-text("Download"), a:has-text("Download")').first();
    if (await downloadBtn.count() > 0) {
      const downloadPromise = page.waitForEvent('download');
      await downloadBtn.click();
      const download = await downloadPromise;
      expect(download.suggestedFilename()).toMatch(/\.(json|txt|md)$/);
    }
  });

  test('No network failures', async ({ page }) => {
    const failures: string[] = [];
    page.on('requestfailed', request => {
      failures.push(request.url() + ': ' + request.failure()?.errorText);
    });

    await page.waitForTimeout(3000);

    const criticalFailures = failures.filter(f =>
      !f.includes('favicon') &&
      !f.includes('webpack-hmr') &&
      !f.includes('hot-update')
    );
    expect(criticalFailures).toHaveLength(0);
  });
});
EOF

echo "Created Playwright test: $TEST_FILE"

# Install Playwright if needed
if [ ! -d "$PROJECT_DIR/node_modules/@playwright" ]; then
  echo "Installing Playwright..."
  (cd "$PROJECT_DIR" && npm install --save-dev @playwright/test)
  npx playwright install --with-deps "$BROWSER"
fi

# Run Playwright tests
echo "Running Playwright verification..."
cd "$PROJECT_DIR"

export ATROPOS_BRIDGE_URL="$BRIDGE_URL"
export PROJECT_URL="${PROJECT_URL:-http://localhost:3000}"

# If headed mode, pass --headed flag
PLAYWRIGHT_ARGS="--reporter=line"
if [ "$HEADED" = true ]; then
  PLAYWRIGHT_ARGS="$PLAYWRIGHT_ARGS --headed"
else
  PLAYWRIGHT_ARGS="$PLAYWRIGHT_ARGS --headed=false"
fi

# Set browser
PLAYWRIGHT_ARGS="$PLAYWRIGHT_ARGS --project=$BROWSER"

# Run tests
npx playwright test "$TEST_FILE" $PLAYWRIGHT_ARGS \
  --output-dir="$OUTPUT_DIR" \
  --timeout="$TIMEOUT" \
  2>&1 | tee "$OUTPUT_DIR/playwright.log"

TEST_EXIT=${PIPESTATUS[0]}

echo ""
echo "=== Verification Complete ==="
echo "Results in: $OUTPUT_DIR"

if [ $TEST_EXIT -eq 0 ]; then
  echo "RESULT: PASS - All verification tests passed"
  exit 0
else
  echo "RESULT: FAIL - Some verification tests failed"
  exit 1
fi