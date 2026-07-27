# Rollback Runbook

## When to roll back

- Post-deployment health check fails
- Deployment smoke test fails
- Migration causes errors
- Configuration mismatch detected
- Security incident requires immediate revert

## Rollback targets

### Cloud Run API

Use the Rollback workflow with target `api` and the previous healthy revision SHA.

The API service retains the previous healthy revision. Rollback updates traffic to point to that revision.

### Cloud Run Worker

Use the Rollback workflow with target `worker` and the previous image tag SHA.

Worker jobs are redeployed with the previous image. Active jobs continue; new jobs use the rolled-back image.

### Vercel

Use the Rollback workflow with target `vercel` and the previous deployment ID.

Vercel retains deployment history. Rollback promotes a previous deployment.

## Rollback process

1. Use the Rollback GitHub Actions workflow
2. Select target, environment, and revision
3. Run with `dry-run=true` first to verify
4. Confirm the revision exists and is healthy
5. Run without dry-run
6. Verify restored traffic

## Database rollback

Do NOT blindly roll back the database. Database rollback requires:

1. Forward-compatible migration (preferred)
2. Controlled restore from verified backup
3. Communication with all affected parties

## Post-rollback

1. Verify health of the rolled-back revision
2. Record the rollback event
3. Investigate the root cause
4. Do not redeploy until root cause is resolved
