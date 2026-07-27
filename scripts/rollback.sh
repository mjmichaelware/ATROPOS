#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"

usage() {
  cat <<EOF
Usage: $(basename "$0") <target> <environment> <revision> [--dry-run]

Roll back a deployment to a specific revision.

Targets:
  api           Cloud Run API service
  worker        Cloud Run Worker job
  vercel        Vercel deployment

Environment:
  staging       Staging environment
  production    Production environment

Revision:
  Immutable revision identifier (commit SHA for Cloud Run, deployment ID for Vercel)

Options:
  --dry-run     Show what would be done without making changes
  --help        Show this message

Examples:
  $(basename "$0") api staging abc123def --dry-run
  $(basename "$0") vercel production dpl_abc123
EOF
  exit 0
}

if [ $# -lt 3 ]; then
  usage
fi

TARGET="$1"
ENVIRONMENT="$2"
REVISION="$3"
DRY_RUN=false

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    --help) usage ;;
  esac
done

# Validation
ERRORS=""
if [ -z "$REVISION" ]; then ERRORS="$ERRORS\n- revision is required"; fi
if [ "$REVISION" = "latest" ]; then ERRORS="$ERRORS\n- revision cannot be 'latest'"; fi
case "$TARGET" in
  api|worker|vercel) ;;
  *) ERRORS="$ERRORS\n- target must be api, worker, or vercel";;
esac
case "$ENVIRONMENT" in
  staging|production) ;;
  *) ERRORS="$ERRORS\n- environment must be staging or production";;
esac

if [ -n "$ERRORS" ]; then
  echo "ROLLBACK VALIDATION FAILED:"
  echo -e "$ERRORS"
  exit 1
fi

echo "=== Rollback Plan ==="
echo "Target:      $TARGET"
echo "Environment: $ENVIRONMENT"
echo "Revision:    $REVISION"
echo "Dry run:     $DRY_RUN"
echo ""

REGION="${REGION:-us-central1}"

rollback_api() {
  if [ "$DRY_RUN" = true ]; then
    echo "[DRY-RUN] gcloud run revisions list --service=specgraph-api --region=$REGION --limit=10"
    echo "[DRY-RUN] Would roll back to specgraph-api-$REVISION"
    echo "Command: gcloud run services update-traffic specgraph-api --region=$REGION --to-revisions=specgraph-api-${REVISION}=100"
    return 0
  fi

  echo "Verifying revision specgraph-api-${REVISION} exists..."
  if ! gcloud run revisions describe "specgraph-api-${REVISION}" --region="$REGION" > /dev/null 2>&1; then
    echo "ERROR: Revision specgraph-api-${REVISION} not found"
    echo "Available revisions:"
    gcloud run revisions list --service=specgraph-api --region="$REGION" --format='value(name)'
    exit 1
  fi

  echo "Rolling back specgraph-api to revision specgraph-api-${REVISION}..."
  gcloud run services update-traffic specgraph-api \
    --region="$REGION" \
    --to-revisions="specgraph-api-${REVISION}=100"

  echo "Verifying rollback..."
  URL=$(gcloud run services describe specgraph-api --region="$REGION" --format='value(status.url)')
  for i in $(seq 1 6); do
    STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$URL/health" 2>/dev/null || echo "000")
    if [ "$STATUS" = "200" ]; then
      echo "Rollback verified: health check passed"
      return 0
    fi
    echo "Attempt $i: health check returned $STATUS, retrying..."
    sleep 5
  done
  echo "WARNING: Rollback applied but health verification incomplete"
}

rollback_worker() {
  if [ "$DRY_RUN" = true ]; then
    echo "[DRY-RUN] Would redeploy specgraph-operation-worker with image revision $REVISION"
    echo "Command: gcloud run jobs deploy specgraph-operation-worker --region=$REGION --image=..."
    return 0
  fi

  echo "Rolling back specgraph-operation-worker to image revision ${REVISION}..."
  gcloud run jobs deploy specgraph-operation-worker \
    --region="$REGION" \
    --image="${REGION}-docker.pkg.dev/${GCP_PROJECT_ID}/specgraph-images/specgraph-operation-worker:${REVISION}" \
    --labels="commit-sha=${REVISION},component=worker,rollback=true"
  echo "Worker rollback applied"
}

rollback_vercel() {
  if [ "$DRY_RUN" = true ]; then
    echo "[DRY-RUN] Would roll back Vercel to deployment $REVISION"
    echo "Command: vercel rollback $REVISION"
    return 0
  fi

  echo "Rolling back Vercel to deployment ${REVISION}..."
  npx vercel rollback "$REVISION" --token="$VERCEL_TOKEN" 2>&1
  echo "Vercel rollback applied"
}

case "$TARGET" in
  api) rollback_api ;;
  worker) rollback_worker ;;
  vercel) rollback_vercel ;;
esac

echo ""
echo "=== Rollback complete ==="
