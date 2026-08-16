# ATROPOS Verification Status

Separate from code completion.

JSON:
{
  "generatedAt": "2026-08-16T08:14:00Z",
  "currentHead": "8bb660da75f4c535fb23157d6fe8a01478b0050d",
  "testsWritten": {
    "status": "ASSESSED",
    "note": "Test obligations are present in the registry where the source requirement explicitly requires a test or acceptance harness"
  },
  "focusedTests": {
    "status": "FOCUSED_PASS",
    "evidence": "Prior ledger evidence: SourceSecretScannerTest and VerifiedCompletionGateTest focused run passed; no focused tests were rerun in accounting pass"
  },
  "fullTests": {
    "status": "NOT_ASSESSED"
  },
  "compile": {
    "status": "LAST_KNOWN_PASS",
    "evidence": "Prior ledger evidence; not rerun in accounting pass"
  },
  "jar": {
    "status": "LAST_KNOWN_PASS",
    "hash": "91dd9af2a43f03c7b486f2a7feed485c25ed376be86691e118fad572c6b8315f",
    "note": "not rebuilt in accounting pass"
  },
  "installedProof": {
    "status": "PASS",
    "goal": "shg-7abcea5c-417",
    "jarHash": "91dd9af2a43f03c7b486f2a7feed485c25ed376be86691e118fad572c6b8315f"
  },
  "restartProof": {
    "status": "PARTIAL",
    "reason": "stale unfinished goal had no ready node; clean startup passed after explicit stop"
  },
  "deployment": {
    "status": "NOT_RUN"
  },
  "releaseStatus": "CODE_INCOMPLETE"
}
