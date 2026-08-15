"""The three stages every requirement passes through.

CONTRACT states what must be true, IMPLEMENTATION builds it, VERIFICATION
confirms it. Named in one place because three modules enforce them -- the
planner that creates a node per stage, the verifier that checks all three exist,
and the executor that runs them in order -- and three copies of this tuple would
eventually disagree about what a stage is.
"""

from __future__ import annotations

STAGES = (
    "CONTRACT",
    "IMPLEMENTATION",
    "VERIFICATION",
)
