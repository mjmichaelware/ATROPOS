# Owner-Only Platform Setup Runbook

## GitHub Actions setup

1. Create GitHub environments: `staging`, `production`, `vercel-preview`, `vercel-production`, `supabase-staging`
2. Add required environment protection rules for production
3. Configure secrets in each environment

## Google Cloud setup (owner)

1. Create Workload Identity Federation pool and provider
2. Configure workload identity provider for GitHub repository
3. Create Artifact Registry repository
4. Create Cloud Run services
5. Grant necessary IAM roles to the service account

## Secrets to configure

### Google Cloud
- `GCP_WIF_PROVIDER`: Workload Identity Federation provider name
- `GCP_PROJECT_ID`: Google Cloud project ID
- `GCP_SERVICE_ACCOUNT`: Deployment service account email
- `GCP_RUNNER_SERVICE_ACCOUNT`: Cloud Run runtime service account

### Supabase
- `SUPABASE_URL`: Project URL
- `SUPABASE_ANON_KEY`: Anon/public key
- `SUPABASE_SERVICE_ROLE_KEY`: Service role key
- `SPECGRAPH_DATABASE_URL`: Direct PostgreSQL connection string
- `SPECGRAPH_CURSOR_SIGNING_KEY`: Cursor pagination signing secret
- `SPECGRAPH_WORKER_STORAGE_TOKEN`: Worker Supabase Storage token

### Vercel
- `VERCEL_TOKEN`: Vercel access token
- `VERCEL_ORG_ID`: Vercel organization/team ID
- `VERCEL_PROJECT_ID`: Vercel project ID
