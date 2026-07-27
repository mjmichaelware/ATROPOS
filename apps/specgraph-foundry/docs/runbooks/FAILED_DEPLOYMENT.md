# Failed Deployment Runbook

## Symptoms

- Workflow run fails
- Health check returns non-200
- Smoke tests fail
- Rollback workflow error

## Immediate actions

1. Do NOT promote traffic to a failed revision
2. The deploy workflow retains the previous healthy revision
3. The deploy workflow does NOT automatically remove failed revisions
4. Run the Rollback workflow if traffic was promoted

## Investigation

1. Check workflow run logs
2. Check Cloud Run logs in Google Cloud Console
3. Check Vercel deployment logs
4. Verify configuration values
5. Check recent commits for breaking changes

## Resolution

1. Fix the root cause
2. Re-deploy with the fix
3. Run through staging first
4. Get re-approval for production
