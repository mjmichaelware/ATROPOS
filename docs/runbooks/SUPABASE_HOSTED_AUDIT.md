# Supabase Hosted Audit Runbook

## Triggering

1. Navigate to GitHub Actions > Supabase Hosted Audit
2. Run workflow with environment `staging`
3. Workflow will only run against staging (production refused)

## Audit checks

- Migration ordering and parity
- RLS policies exist on relevant tables
- Storage bucket configuration
- PostgreSQL boolean parameter handling
- Supabase configuration validity

## When credentials are unavailable

The audit workflow detects unavailable credentials and runs static checks only.
Static checks include migration file analysis and contract validation without connecting to Supabase.

## Results

- A sanitized report is produced (no secrets, URLs, tokens, or private records)
- Pass/fail status per check
- Migration count and RLS policy count
