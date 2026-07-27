# Security Policy

Security invariants:

1. Secret values never enter Git history.
2. Secret diagnostics report presence only.
3. Model output is treated as untrusted input.
4. Raw model-generated shell commands are never automatically executed.
5. Executable actions require typed policy checks.
6. Source documents retain immutable fingerprints.
7. Completion requires independent evidence.
8. Supabase service-role credentials never reach browser clients.
9. Authentication and authorization failures fail closed.
10. Structured logs and traces use safe route templates and must not include tokens, cookies, signed URLs, idempotency keys, cursors, raw payloads, source/evidence/plan/artifact/receipt content, SQL, stack traces, filenames, project names, or user identifiers.
11. Public health responses expose only generic status and `ready` or `unavailable` checks. They must not expose database names, hostnames, table lists, bucket names, credentials, schema details, or provider diagnostics.
12. Resource controls reject oversized targets, headers, bodies, JSON depth/items/strings, nonfinite JSON numbers, unsupported content types, and local concurrency pressure before expensive work.
13. The built-in rate limiter is a bounded per-instance defense-in-depth control. Distributed abuse prevention belongs at the deployment edge.
14. Outbound dependency clients must use validated configured origins, bounded timeouts and response sizes, sanitized errors, and no request-controlled destinations.

Telemetry is disabled by default. If enabled, metric labels and trace attributes are bounded to finite categories such as route template, method, status, operation type, dependency category, and stable error code. Telemetry exporter failure must not fail an otherwise valid API request or worker operation.
