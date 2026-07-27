ATROPOS SOURCE DOCUMENTS 1 AND 2 — ABSOLUTE DAG COMPLETION MANDATE

You are the autonomous implementation director for the ATROPOS repository located at:

"/data/data/com.termux/files/home/ATROPOS"

Your assignment is not to produce a plan, summary, recommendation, or next-pass prompt. Your assignment is to continue implementing, testing, repairing, and verifying the repository until every atom in the authoritative DAG derived from Source Document 1 and Source Document 2 is genuinely complete.

Non-termination rule

Do not stop merely because:

- One atom was completed.
- One test suite passed.
- A response is becoming long.
- Context was compacted.
- A secondary defect was discovered.
- An implementation requires several debugging cycles.
- A dependency or prior implementation is defective.
- A previous agent claimed something was complete.

Continue with the next highest-priority incomplete atom after every verified atom.

At the beginning of every continuation cycle, reread this mandate, the authoritative DAG, the completion ledger, and the current repository state.

Mandatory initial discovery

Before modifying anything:

1. Read "AGENTS.md" and every applicable nested "AGENTS.md".
2. Run "pwd", "git status --short --branch", and inspect recent commits.
3. Locate the authoritative files for:
   - Source Document 1
   - Source Document 2
   - the SD1/SD2 atom DAG
   - the completion ledger
   - acceptance criteria and test commands
4. Search for these identifiers and phrases:
   - "A_SOURCE_AUTHORITY_DLOI"
   - "A004"
   - "HIGZeroGuard"
   - "Source Document 1"
   - "Source Document 2"
   - "completion ledger"
   - "DAG"
5. Read every byte of both source documents and the authoritative DAG. Read large files in consecutive chunks until EOF rather than relying on summaries.
6. Record the authoritative paths, sizes, hashes, and current incomplete-atom count in:
   "docs/automation/SD1_SD2_AUTONOMOUS_STATUS.md"

Do not invent missing requirements. Requirements must be grounded in Source Documents 1 and 2, the DAG, repository contracts, and executable tests.

Resume point

Resume from the highest-priority incomplete dependency atom.

The immediate known blocker is A004 under "A_SOURCE_AUTHORITY_DLOI".

Current evidence indicates that "HigZeroGuard.kt", "DloiService.kt", and "HigZeroGuardTest.kt" have already been modified, but DLOI tests still fail because the hand-written JSON extraction logic may mistake braces or ""sections": [" text inside JSON string values for structural JSON.

First:

1. Preserve and inspect all existing modifications.
2. Run the failing DLOI test with a complete stacktrace.
3. Identify the exact file, parser function, and input causing failure.
4. Inspect existing Gradle dependencies before adding anything.
5. Replace fragile regex-based structural JSON parsing with a real JSON parser already available to the build where possible.
6. Do not silently skip malformed indexed documents merely to make tests pass unless Source Documents 1 and 2 explicitly require that behavior.
7. Add regression fixtures covering:
   - braces inside string values
   - escaped quotes
   - embedded ""sections": [" text inside strings
   - nested objects and arrays
   - malformed JSON
   - exact source-address lookup
   - typed "NoMatch"
   - HIG=0 behavior
8. Run all 15 DLOI tests and the relevant broader test gate.
9. Mark A004 complete only after its acceptance criteria and tests pass.

Then immediately continue to the next incomplete DAG atom.

Atom execution protocol

For each atom:

1. Identify its exact source authority and dependencies.
2. Read the existing implementation and tests before editing.
3. State the acceptance criteria internally.
4. Implement the smallest complete production solution.
5. Do not create stubs, placeholders, fake implementations, hardcoded test answers, TODO-only code, boilerplate substitutes, or simulated integrations.
6. Add or strengthen tests for normal, edge, malformed, adversarial, and regression cases.
7. Run the narrowest relevant test first.
8. Repair every failure caused by the atom.
9. Run the broader affected suite.
10. Run formatting, compilation, static checks, and repository gates required by the project.
11. Update the authoritative completion ledger with:
    - atom identifier
    - status
    - files changed
    - commands run
    - test results
    - evidence
    - remaining blockers
12. Continue to the next dependency-ready incomplete atom.

Never mark an atom complete based solely on code existing.

Repository safety

- Never run "git reset --hard".
- Never run "git clean".
- Never discard, overwrite, or revert unrelated existing work.
- Never delete tests to obtain a pass.
- Never weaken assertions merely to hide defects.
- Never modify generated or vendored files when the source file should be changed.
- Never expose credentials or commit secrets.
- Do not push to a remote repository unless explicitly authorized.
- Preserve unrelated uncommitted changes.
- Keep changes within ATROPOS unless an authoritative project instruction explicitly requires otherwise.

Blocker handling

Do not terminate the whole run because one atom is externally blocked.

When blocked:

1. Record the exact blocker and evidence.
2. Distinguish a true external blocker from an implementation problem.
3. Continue every independent atom that can still be completed.
4. Return to blocked atoms whenever their dependencies become available.
5. Do not ask the user questions that can be answered by reading the repository, documents, logs, history, or tests.

Context recovery

Whenever context is compacted or uncertain:

1. Reread this file.
2. Reread Source Documents 1 and 2.
3. Reread the authoritative DAG and ledger.
4. Read "docs/automation/SD1_SD2_AUTONOMOUS_STATUS.md".
5. Inspect "git status" and recent diffs.
6. Resume from the highest-priority incomplete atom.

Do not restart completed work merely because conversational context was lost.

Completion conditions

The project is complete only when all of the following are true:

1. Every authoritative SD1/SD2 DAG atom is marked complete with evidence.
2. No required atom remains OPEN, TODO, PARTIAL, FAILED, UNKNOWN, or falsely satisfied by a stub.
3. All required unit, integration, regression, and end-to-end tests pass.
4. The complete Gradle build and repository verification gates pass.
5. No tracked source file contains prohibited completion placeholders for required behavior.
6. Source-document requirements are traceable to implementation and tests.
7. The final ledger is internally consistent.
8. "git diff --check" passes.
9. A final forensic audit finds no omitted requirement.

Only after independently verifying every condition, write this exact line by itself in:

"docs/automation/SD1_SD2_AUTONOMOUS_STATUS.md"

"ABSOLUTE_COMPLETION: VERIFIED"

Do not write that marker early.

After verified completion, create:

"docs/automation/SD1_SD2_ABSOLUTE_COMPLETION_REPORT.md"

The report must include the final atom totals, requirement coverage, files changed, tests and build commands, results, unresolved external limitations if any, and the final Git status.

Begin execution immediately. Do not merely explain this mandate back to the user.
