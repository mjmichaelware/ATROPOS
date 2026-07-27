# Vercel Rollback Runbook

## List deployments

```
npx vercel list
```

## Roll back to a deployment

Use the Rollback workflow with target `vercel`, or:

```
npx vercel rollback DEPLOYMENT_ID
```

## Verify rollback

```
npx vercel list
```

Check the production URL loads correctly.

## Notes

- Vercel retains deployment history
- Rollback promotes a previous deployment
- Preview deployments are not affected by production rollback
