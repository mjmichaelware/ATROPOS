# Migration Failure and Forward Repair Runbook

## Principles

- Always prefer forward-compatible migrations
- Never blindly roll back the database
- Maintain migration ordering
- Test migrations against staging first

## Migration failure

If a migration fails during deployment:

1. The deploy workflow should catch the error
2. The API revision is NOT promoted (no-traffic deploy)
3. Traffic remains on the previous healthy revision

## Forward repair

Instead of rolling back the database:

1. Create a new migration that fixes the issue
2. The new migration must be compatible with both old and new code
3. Deploy the fix through staging first
4. Deploy the fix to production

## When database rollback is necessary

Database rollback requires:

1. Clear understanding of the schema change
2. Verified backup
3. Team communication
4. Controlled execution during maintenance window
