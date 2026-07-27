# Production Approval Runbook

## Approval gates

Production deployment requires approval from a repository owner or authorized deployer.

## Approval process

1. CI checks pass on the target commit
2. Staging deployment completes successfully
3. Staging acceptance tests pass
4. Release SHA is verified
5. Rollback target is recorded
6. Previous healthy revisions are available
7. Protected environment approval is granted in GitHub Actions

## After approval

1. Run production deployment workflows
2. Monitor deployment progress
3. Run production smoke tests
4. Verify telemetry and logging
5. Confirm rollback target works
