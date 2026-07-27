# Telemetry Validation Runbook

## Check configuration

Verify environment variables:
- `SPECGRAPH_OTEL_ENABLED` is true for production
- `SPECGRAPH_OTEL_EXPORTER_OTLP_ENDPOINT` points to the collector
- `SPECGRAPH_OTEL_SERVICE_NAME` matches deployment
- `SPECGRAPH_TRACE_SAMPLE_RATIO` is appropriate

## Validate telemetry startup

1. Deploy the API
2. Check startup logs for telemetry initialization
3. Verify trace export in the configured backend

## Verify structured logging

1. Check that logs are in JSON format (`SPECGRAPH_LOG_FORMAT=json`)
2. Verify log level is appropriate per environment
3. Check that sensitive data is redacted from logs
