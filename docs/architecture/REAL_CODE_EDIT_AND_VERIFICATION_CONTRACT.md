# Real Code Edit And Verification Contract

Status: implementation contract for the existing provider-to-code path.

## Product invariant

An accepted response is not a completion claim. ATROPOS must produce a real
source mutation, preserve the existing territory and policy gates, run the
repository's applicable checks, consume their bounded diagnostics, and repeat
with a repair proposal until the checks pass or the bounded retry budget ends.
`COMPLETED` is valid only with source, wiring, verification, and evidence.

## Edit protocol

The canonical mutation owner remains `AgentPatchStore` and its existing
territory, bounded-agency, auditor, redaction, and verification collaborators.
Provider output is now accepted in two forms:

1. Strict edit envelopes, preferred for generation:
   - `<atropos-create path="...">...complete file...</atropos-create>`
   - `<atropos-rewrite path="...">...complete file...</atropos-rewrite>`
   - `<atropos-replace path="...">` with exactly one `SEARCH` and `REPLACE`
     block.
2. Unified diff, retained as a compatibility format.

`AgentEditDecoder` rejects malformed or ambiguous envelopes. `AgentEditMaterializer`
reads the current bounded repository context, checks path containment, optional
source hashes, file existence, and exact match cardinality, then emits the
existing patch record shape. No second writer or gate is introduced. A stale,
ambiguous, or approximate edit is refused before persistence or mutation.

Whole-file operations are appropriate for new files and deliberate rewrites;
exact replacement is preferred for existing files. Large repositories must
partition work by files and affected dependency/test slices. They must not move
100,000-line files through a provider prompt merely to change one declaration.

## Language and scale contract

The factory detects the requested ecosystem and emits only a layout it can
actually own. A language without a scaffold is reported as unsupported rather
than silently receiving Kotlin files. A future language adapter must provide:

- source and test layout;
- formatter/linter and build/test commands;
- bounded diagnostic parsing with file and line coordinates;
- a deterministic verification command;
- redaction, territory, and evidence integration.

Unknown languages may receive provider-authored files, but cannot receive a
verified completion claim until a repository-specific verifier is available.

## Research basis

The contract follows the verified loop documented by Claude Code: gather context,
take an action, and verify the result. Aider documents whole-file and multiple
diff/edit formats with different reliability and cost tradeoffs. OpenHands
documents a software-engineering SDK with tool and sandbox boundaries. Codex
CLI documents the same essential surface: read, modify, and run code.

These products do not make file emission equivalent to success. The common
quality property is the bounded write/build-diagnose/repair loop, with explicit
permissions and checkpoints around mutation.

## Non-goals

- This contract does not create a second DAG, verifier, territory system,
  memory root, provider registry, or artifact pipeline.
- It does not claim arbitrary-language verification without an adapter.
- It does not run a full build as part of source editing.
