# Backup and Restore Runbook

## Scheduled backups

Supabase provides automated backups for production projects.
Verify backup schedule in the Supabase dashboard.

## Manual backup

```
supabase db dump -f specgraph-backup-$(date +%Y%m%d).sql
```

## Restore

```
supabase db restore specgraph-backup-20260714.sql
```

Restore should only be performed:
- After team communication
- During a maintenance window
- After confirming the backup is valid

## Verify backup

1. Restore to a staging environment
2. Run health checks
3. Verify data integrity
4. Run the hosted acceptance smoke tests

## Backup retention

- Keep daily backups for 30 days
- Keep weekly backups for 6 months
- Keep monthly backups for 1 year
