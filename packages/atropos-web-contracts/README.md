# ATROPOS Web Contracts
This package boundary is reserved for framework-neutral presentation contracts shared by ATROPOS surfaces and bounded SpecGraph integration.
## Allowed
- project identity projections
- human-facing status vocabulary
- explainability action contracts
- trust indicator projections
- notification envelopes
- safe event-stream envelopes
- route ownership metadata
- theme and accessibility tokens
## Forbidden
- direct database access
- provider invocation
- Git execution
- compiler implementation
- SpecGraph semantic graph mutation
- ATROPOS policy mutation
- secret-bearing payloads
- framework-specific components
## Dependency Direction
Backend domain state -> safe projection -> web contract -> renderer.
Renderer action -> typed intention -> governed application command.
