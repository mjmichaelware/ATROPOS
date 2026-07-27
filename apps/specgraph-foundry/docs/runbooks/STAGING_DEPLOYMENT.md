# Staging Deployment Runbook

## Prerequisites

- GitHub Actions workflow permissions
- Google Cloud Workload Identity Federation configured
- Vercel project connected
- Supabase staging project available

## Deploy API to staging

1. Navigate to GitHub Actions > Deploy Cloud Run API
2. Click "Run workflow"
3. Set environment to `staging`
4. Leave SHA blank (uses current HEAD)
5. Click "Run"

## Deploy worker to staging

1. Navigate to GitHub Actions > Deploy Cloud Run Worker
2. Click "Run workflow"
3. Set environment to `staging`
4. Leave SHA blank
5. Click "Run"

## Deploy web to staging/preview

1. Navigate to GitHub Actions > Deploy Vercel
2. Click "Run workflow"
3. Set environment to `preview`
4. Leave SHA blank
5. Click "Run"

## Verify staging deployment

1. Check the deploy workflow completes successfully
2. Verify API health endpoint returns 200
3. Verify Vercel preview URL loads
4. Run Supabase Hosted Audit workflow
