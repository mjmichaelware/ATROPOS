# Cloud Run Revision Recovery Runbook

## List revisions

```
gcloud run revisions list --service=specgraph-api --region=us-central1
gcloud run revisions list --service=specgraph-operation-worker --region=us-central1
```

## Describe a revision

```
gcloud run revisions describe specgraph-api-REVISION_SHA --region=us-central1
```

## Restore a revision

Use the Rollback workflow or:

```
gcloud run services update-traffic specgraph-api \
  --region=us-central1 \
  --to-revisions=specgraph-api-REVISION_SHA=100
```

## Verify restored revision

```
curl -sf $(gcloud run services describe specgraph-api --region=us-central1 --format='value(status.url)')/health
```
