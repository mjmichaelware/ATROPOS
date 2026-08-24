# Open-core boundary

ATROPOS keeps the planner, policy gates, provider routing, bridge, and client
surfaces in the open repository. The local evidence store, content-addressed
artifacts, governance journal, and source-authority checks remain authoritative
when the engine runs offline.

Optional hosted services may synchronize evidence or ledger projections, but
they are additive transports. They are not a second planner, verifier,
provider registry, authority store, or event bus, and local-only mode disables
remote research and remote MCP transports while allowing explicitly allowlisted
local stdio tools.

This repository is licensed under AGPL-3.0-only. Operators distributing a
modified network service should review the corresponding source-availability
obligations, including AGPL section 13, with their legal counsel. This note is
informational and does not replace the license text in [`LICENSE`](../LICENSE).
