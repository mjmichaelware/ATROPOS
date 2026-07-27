# ADR-008 Observability, Security, and Resource Controls

Status: Accepted for implementation.

## Context

The repository currently provides request-size bounds, secret rejection in integration bindings, and ownership tests, but it does not yet implement structured production observability, OpenTelemetry-compatible telemetry, or comprehensive hosted resource and security controls.

## Decision

- Use structured redacted JSON logs plus OpenTelemetry-compatible traces and metrics.
- Never log authority content, tokens, secrets, signed URLs, or private payload bodies.
- Enforce explicit quotas, rate limits, request bounds, upload bounds, and decompression bounds.
- Enforce SSRF controls and parser hardening for uploaded document adapters.
- Ship CSP, HSTS, referrer-policy, permissions-policy, and safe CORS settings.
- Add dependency scanning, secret scanning, and OWASP-oriented security tests.
- Expose health, startup, and readiness checks without sensitive leakage.

## Detailed Topology or Contract

- Log schema includes request id, route, owner scope, operation id, duration, result code, and bounded diagnostics.
- Trace and metric schema covers API latency, worker latency, queue depth, upload lifecycle, auth failures, storage failures, and retry behavior.
- Resource controls include:
  - bounded request body size
  - bounded upload size
  - bounded decompressed payload size
  - bounded parser time and memory
  - per-user and per-IP rate limits where applicable
- Security headers are enforced on web and API surfaces with exact allowed origins.
- Adapter execution must restrict outbound fetches, unsafe URI schemes, and ambiguous parser behaviors.

## Security Consequences

- Redaction rules become mandatory because source authorities and tokens can be highly sensitive.
- SSRF and parser hardening are required once binary uploads and external adapters exist.
- Security scanning becomes part of the release gate rather than an optional review step.

## Data/Migration Consequences

- Observability metadata may introduce new operational tables or external telemetry sinks, but domain authority data remains unchanged.
- Quota and rate-limit state may require dedicated persistence or managed services.

## Testing Consequences

- Security tests must cover oversized requests, malformed uploads, policy headers, secret redaction, SSRF rejection, and rate limiting.
- Telemetry tests must verify that logs and traces exclude secrets and signed URLs.

## Operational Consequences

- Production support gains diagnosable signals for API, worker, upload, and auth failures.
- Alerting thresholds and quota exhaustion policies must be defined before hosted release.
- Readiness checks must distinguish startup failure from downstream partial degradation without leaking private details.

## Rejected Alternatives

- Plaintext ad hoc logs only: rejected because they are hard to query and easy to leak data through.
- Logging full request and response bodies for debugging: rejected because source authorities, tokens, and signed URLs must not enter logs.
- Deferring security scanning until after deployment: rejected because release gating needs pre-deploy evidence.

## Dependencies on Later Groups

- Group 09 for runtime security and observability
- Group 19 for CI security gates
- Group 20 for hosted acceptance evidence
