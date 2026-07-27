# ADR-003 Source Storage and Upload Pipeline

Status: Accepted for implementation.

## Context

The current backend ingests text payloads directly and stores canonical source content in the database, but the repository has no hosted binary upload pipeline, no private storage bucket design, and no artifact-storage separation. Production uploads need immutable authority preservation without leaking secrets or signed URLs.

## Decision

- Original uploaded binaries live in private Supabase Storage.
- Upload initiation returns a short-lived signed or resumable upload target.
- Finalize verifies owner, path, byte count, SHA-256, content type, and single-use semantics before ingestion.
- Canonical extracted UTF-8 text becomes the existing immutable source authority.
- Adapters and adapter versions record derivation metadata.
- Exports use a separate private artifact bucket.

## Detailed Topology or Contract

- Source bucket:
  - private
  - object keys partitioned by environment, owner, project, and upload identifier
- Upload initiation endpoint:
  - authenticates owner
  - reserves upload metadata
  - returns a short-lived signed or resumable target
- Finalize endpoint:
  - proves the caller owns the reserved upload
  - verifies the stored object path
  - verifies byte count
  - verifies SHA-256
  - verifies media type against the allowed adapter set
  - consumes the reservation exactly once
- Ingestion step:
  - reads the uploaded binary
  - runs the selected adapter
  - stores canonical UTF-8 text as immutable source authority using existing provenance semantics
  - records adapter name, adapter version, source object reference, and derivation metadata
- Export artifacts are written to a different private bucket so source authority and generated artifacts never share the same retention or access path.

## Security Consequences

- Private buckets prevent direct public access to raw source material and generated artifacts.
- Signed or resumable targets must expire quickly and never be logged.
- Finalize verification prevents object-path spoofing, size mismatches, and replay of consumed uploads.

## Data/Migration Consequences

- New upload reservation, source object reference, and derivation metadata records are required.
- Existing source-document tables continue to hold canonical extracted authority text and provenance.
- Artifact bucket references must be stored separately from source-object references.

## Testing Consequences

- Tests must cover reservation replay, owner mismatch, hash mismatch, byte mismatch, unsupported type rejection, and single-use finalize enforcement.
- Adapter tests must prove derivation metadata and canonical text consistency.
- Storage tests must verify that signed URLs expire and are not persisted in durable records.

## Operational Consequences

- Storage lifecycle management must cover raw source retention and artifact retention separately.
- Upload support requires operational visibility into incomplete uploads, abandoned reservations, and finalize failures.
- Large uploads need bounded sizes and resumable handling appropriate for mobile and browser clients.

## Rejected Alternatives

- Storing original binaries directly in PostgreSQL rows: rejected because storage growth, transport cost, and object lifecycle controls are worse.
- Making source uploads public: rejected because authority documents can contain sensitive proprietary content.
- Reusing the source bucket for exports: rejected because it mixes source authority with generated artifacts.

## Dependencies on Later Groups

- Group 05 for source storage and upload implementation
- Group 06 for adapter and provenance expansion
- Group 07 for artifact storage
- Group 11 and Group 12 for frontend upload and provenance UX
