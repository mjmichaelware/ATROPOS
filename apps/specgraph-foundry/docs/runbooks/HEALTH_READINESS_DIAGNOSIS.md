# Health and Readiness Diagnosis Runbook

## API health endpoint

```
GET /health
```

Returns `{"status": "ok", "service": "specgraph-foundry"}`

## API readiness endpoint

```
GET /readiness
```

Returns readiness check with database, storage, and operations status.

## Startup endpoint

```
GET /startup
```

Returns startup check with configuration and schema status.

## Common failure modes

### Database unavailable

Check:
- SPECGRAPH_DATABASE_URL is correct
- Database server is reachable
- Connection pool is not exhausted
- Migration state is compatible

### Storage unavailable

Check:
- SUPABASE_URL and service role key are correct
- Storage bucket exists
- Bucket RLS policies allow access

### Operations unavailable

Check:
- Worker is running
- Operations table exists
- Worker service account has correct permissions
