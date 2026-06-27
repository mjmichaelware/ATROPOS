# ATROPOS Tier H Addendum

Tier H covers phases 14-16: security/redaction, deterministic test matrix, and deployment operations.

Acceptance rules:
- Redaction runs before display, persistence, queued work, and model prompt context.
- Secret source precedence is explicit, deterministic, and redacted in status output.
- Provider tests do not require live keys.
- Ops exports are local-first and do not require GitHub, Google, Supabase, Pinecone, or Cloudflare.
- The pass is accepted only after narrow compile, full compile, CLI smoke, secret diff check, and clean checkpoint.
