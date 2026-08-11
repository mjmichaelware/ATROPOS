# Parallelism Allowlist

This file is intentionally small. A duplicate-looking path is allowed only when its responsibility is independently required and the owner boundary is explicit.

| Paths | Reason | Why shared owner is insufficient | Review condition | Verifying evidence |
|---|---|---|---|---|
| `apps/specgraph-foundry/src/**` and `src/main/kotlin/**` | SpecGraph is a Python planning substrate and ATROPOS is a Kotlin execution engine | language/runtime boundary is a real deployment contract; neither may own the other’s domain policy | review at Phase 11/20 handoff | source binding and typed handoff tests |
| `apps/specgraph-foundry/supabase/migrations/**` historical records | applied migration identity may be externally durable | deleting or renaming applied history can corrupt deployment state | remove once remote history is verified and one future directory is gated | migration parity/security tests |
| platform adapters behind shared contracts | runtime/platform constraints | adapter implementations are required at the port boundary | each adapter must contain no domain policy | architecture checker |
