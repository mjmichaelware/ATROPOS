# ATROPOS Code-Base Completion Accounting Specification

## Law
CODE COMPLETION answers only how much of the source-authoritative vision is implemented in the repository. Tests, builds, JARs, installation, restart, deployment, Git cleanliness, and operator proofs are separate axes.

## Code-base obligation set
The denominator is the frozen union of the Source Docs 1-3 atoms, Source Doc 4 acceptance obligations, and accepted Blueprint obligations. The Core, HOE, and Phase 20 gap maps are all hashed and crosswalked; because they restate or operationalize existing requirements, they do not create duplicate feature credit. Each counted obligation has a stable ID, exact source coordinate, source hash, one phase, one canonical owner, and binary status.

## Binary rule
Each source atom is decomposed into independently scored `implementation`, `integration`, and `semantics` predicates. An implementation predicate requires explicit canonical source files and no missing/stub source status. An integration predicate requires a static caller/reference outside the owner set. A semantics predicate requires a static test/evidence reference. A predicate is WRITTEN only when its own evidence rule passes; one file cannot silently satisfy all three predicates. Empty or directory-only evidence is not implementation proof. Every accepted evidence path is SHA-256 recorded in the registry. Duplicate implementations do not add credit.

## Formula
phase_code_completion = written_obligations / total_implementation_obligations * 100. The same obligation-count formula applies to checkpoints and the whole vision. Phase percentages are never averaged and have no subjective size weights.

## Separate axes
ATROPOS_VERIFICATION_STATUS.md records tests, compile/build, JAR, installed, restart, deployment, and release status. Those statuses never lower code completion. Byte hashes prove the identity and reproducibility of evidence; they cannot by themselves prove semantic behavior, which is why integration and semantics predicates remain separate.

## Authority and freeze
Human Owner instruction, Source Documents 1-4, accepted amendments and blueprints, gap maps, phase maps, AGENTS control law, then code/tests as evidence. Source Documents 1-4 are immutable. Denominator amendments require accepted authority, duplicate correction, phase remapping, or equivalent finer-grained decomposition and are append-only.
