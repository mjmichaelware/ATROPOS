# Production Deployment Runbook

## Prerequisites

- All CI checks pass on the target commit
- Staging acceptance tests pass
- Protected environment approval obtained
- Rollback target recorded
- Previous healthy revision confirmed available

## Deployment order

1. Deploy API to production (Cloud Run)
2. Verify API health
3. Run migrations (forward-compatible)
4. Deploy worker to production
5. Verify worker
6. Deploy web to production (Vercel)
7. Verify web
8. Run Supabase hosted audit
9. Run production smoke tests

## Deploy API

1. Navigate to GitHub Actions > Deploy Cloud Run API
2. Run workflow with environment `production` and exact SHA
3. Wait for protected environment approval

## Deploy worker

1. Navigate to GitHub Actions > Deploy Cloud Run Worker
2. Run workflow with environment `production` and exact SHA
3. Wait for protected environment approval

## Deploy web

1. Navigate to GitHub Actions > Deploy Vercel
2. Run workflow with environment `production` and exact SHA
3. Wait for protected environment approval

## Verify production

1. Check each workflow completed successfully
2. Verify production API health
3. Verify production web loads
4. Verify rollback target recorded
5. Confirm no unresolved blockers
