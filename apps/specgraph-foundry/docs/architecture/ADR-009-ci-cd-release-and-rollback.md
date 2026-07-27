# ADR-009 CI/CD, Release, and Rollback

Status: Accepted for implementation.

## Context

The repository has one CI workflow that compiles Python, runs tests, checks licenses, and runs CLI smoke checks. It has no deployment automation, no protected preview policy, no OIDC-based cloud delivery, and no documented rollback or incident procedure.

## Decision

- GitHub Actions performs locked tests, audits, builds, and security checks.
- Google deployment uses OIDC and Workload Identity Federation rather than long-lived JSON keys.
- Vercel previews are protected.
- Production environments require approval.
- Migrations precede compatible API rollout.
- Frontend follows API verification.
- Prior Cloud Run and Vercel revisions are retained.
- Roll back application revisions without blindly rolling the database backward.
- Backup, restore, and incident runbooks are required.

## Detailed Topology or Contract

- CI stages include backend tests, web tests, contract validation, generated-client validation, security scanning, and build verification.
- Deployment identity:
  - GitHub Actions federates into Google Cloud with OIDC
  - GitHub Actions uses the Vercel deployment integration without long-lived cloud keys committed to the repository
- Promotion order:
  - apply forward-compatible migrations
  - verify API deployment
  - verify generated client compatibility
  - deploy frontend
  - run staged acceptance checks
- Rollback model:
  - revert Cloud Run service revision if API release is bad
  - revert Vercel deployment if frontend release is bad
  - avoid blind database down-migrations; use forward fixes, backups, or controlled restore paths

## Security Consequences

- OIDC and Workload Identity Federation remove the need for static Google deployment keys.
- Protected previews reduce the risk of exposing private staging data to unauthenticated users.
- Approval gates reduce accidental promotion of unverified migrations or builds.

## Data/Migration Consequences

- Migrations must be ordered and compatible with the deployed API and web revisions during rollout.
- Backup and restore procedures become mandatory before production release.
- Release automation must record which application revisions ran against which migration state.

## Testing Consequences

- CI must fail on contract drift, security scan failures, migration errors, or rollback procedure breakage.
- Staging acceptance tests must run against the deployed topology before production approval.

## Operational Consequences

- Release management needs environment approvals, deploy logs, revision identifiers, and rollback rehearsals.
- Incident response requires documented backup, restore, and communication runbooks.
- Old revisions must be retained long enough for safe rollback during rollout windows.

## Rejected Alternatives

- Manual production deploys from local machines: rejected because they are hard to audit and reproduce.
- Long-lived JSON service account keys in GitHub secrets as the default path: rejected because OIDC federation is safer.
- Automatic database rollback on every application rollback: rejected because schema state can diverge and corrupt recovery.

## Dependencies on Later Groups

- Group 19 for CI/CD and rollback implementation
- Group 20 for hosted release verification and runbooks
- Group 02 and Group 10 because contract and web builds must be part of the release graph
