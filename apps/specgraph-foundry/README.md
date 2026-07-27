# SpecGraph Foundry 🔥

> **Source documents are not prompts. They are compilable authority.**

**Byte-complete provenance • Research-enriched plans • Independently verifiable handoffs • Mathematically superior architecture**

![SpecGraph Foundry Hero](docs/assets/specgraph-hero.png)
*(Add the generated professional hero banner showing document ingestion → authority graph → execution DAG → verified handoff to ATROPOS)*

SpecGraph Foundry is the **high-fidelity planning and verification substrate** of a new sovereign software development paradigm. It transforms complex, messy source documentation into complete, research-backed, machine-verifiable software blueprints with uncompromising provenance and correctness guarantees.

While other tools treat requirements as vague prompts for AI generation, SpecGraph treats them as **compilable authority** — immutable, addressable, researchable, and independently verifiable.

---

## The Vision: Planning at the Level of Execution

Most software projects fail not because of bad code, but because of **lost context, unverified assumptions, and superficial planning**.

Current approaches either:
- Rely on LLM prompting (high hallucination, no provenance)
- Use lightweight task lists or kanban (no deep research or verification)
- Produce plans that cannot be independently audited

**SpecGraph Foundry solves this at the architectural level.**

It delivers:
- **Byte-complete ingestion** with SHA-256 fingerprints and exact coordinates
- **Atomic requirement extraction** across 16 completeness dimensions
- **Real research evidence engine** with leasing, reliability scoring, and gap matrices
- **Dual-graph architecture**: Authority Graph (cycles allowed for real-world relationships) + Execution DAG (strict ordering for safe implementation)
- **Verified handoff exports** designed for deterministic execution engines like ATROPOS
- **Independent verification gates** that reject empty implementations, self-verification, and source-less requirements

This is not another planning tool. This is **infrastructure for trustworthy, long-horizon software development**.

---

## Mathematical & Architectural Superiority

| Dimension                        | Typical Planning / Agent Tools                  | SpecGraph Foundry                                      | Why It Is Superior |
|----------------------------------|------------------------------------------------|-------------------------------------------------------|--------------------|
| **Provenance**                   | Weak or absent                                 | Byte-complete SHA-256 + exact line/byte addressing    | True auditability from day one |
| **Research Quality**             | LLM guesswork or shallow search                | Evidence-leased tasks with reliability scoring        | Bounded, auditable, non-hallucinated research |
| **Graph Modeling**               | Single graph or none                           | Authority Graph + Execution DAG (cycles vs acyclic)   | Correctly models both relationships and safe ordering |
| **Verification**                 | Post-hoc or self-referential                   | Independent gates + runtime receipt evaluation        | No self-verification theater |
| **Handoff Integrity**            | Ad-hoc or lossy                                | Checksummed, deterministic export bundles             | Safe consumption by execution engines |
| **Scaling & Governance**         | Uncontrolled agent chat or simple workflows    | Policy-controlled routing + explicit paid unlocks     | Cost discipline and intentionality by design |
| **Long-term Maintainability**    | Context loss over time                         | Persistent, queryable authority + research history    | Institutional memory that compounds |

**SpecGraph is mathematically superior in provenance fidelity, research rigor, graph correctness, and verification independence.**

---

## Architecture Overview

```mermaid
flowchart LR
    A[Source Documents<br/>Byte-Complete + SHA-256] --> B[Immutable Ingestion<br/>Exact Coordinates + Deduplication]
    B --> C[Atomic Extraction<br/>16 Completeness Dimensions]
    C --> D[Research Evidence Engine<br/>Leasing + Reliability + Gap Matrix]
    D --> E[Authority Graph<br/>Relationships • Conflicts • Decisions]
    E --> F[Execution DAG Synthesis<br/>Safe Ordering + Readiness Scoring]
    F --> G[Verified Handoff Export<br/>atropos_handoff.json + Checksums]
    G --> H[ATROPOS Execution Engine]
    H --> I[Domain Applications<br/>MusicMakerLM + Future Apps]