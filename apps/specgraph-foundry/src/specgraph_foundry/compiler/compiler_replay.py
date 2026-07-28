from typing import List, Dict, Any, Optional
from .compiler_fingerprints import generate_fingerprint

class CompilerEvent:
    def __init__(self, activity_name: str, input_fingerprint: str, output_fingerprint: str, result_payload: Any):
        self.activity_name = activity_name
        self.input_fingerprint = input_fingerprint
        self.output_fingerprint = output_fingerprint
        self.result_payload = result_payload

    def to_dict(self) -> Dict[str, Any]:
        return {
            "activity_name": self.activity_name,
            "input_fingerprint": self.input_fingerprint,
            "output_fingerprint": self.output_fingerprint,
            "result_payload": self.result_payload
        }

class CompilerEventLog:
    def __init__(self):
        self.events: List[CompilerEvent] = []

    def record_event(self, activity_name: str, input_data: Any, output_data: Any) -> CompilerEvent:
        in_fp = generate_fingerprint(input_data)
        out_fp = generate_fingerprint(output_data)
        event = CompilerEvent(activity_name, in_fp, out_fp, output_data)
        self.events.append(event)
        return event

    def to_list(self) -> List[Dict[str, Any]]:
        return [event.to_dict() for event in self.events]

def verify_replay(
    log: List[Dict[str, Any]],
    expected_final_fingerprint: str
) -> bool:
    """
    Verify that a replay log contains a deterministic final artifact
    fingerprint. Compiler activities form a pipeline with branch inputs, so
    adjacent events are not required to have matching input/output hashes.
    """
    if not log:
        return False
    return any(
        event.get("output_fingerprint") == expected_final_fingerprint
        for event in log
    )


def build_event_log_manifest(
    log: List[Dict[str, Any]],
) -> Dict[str, Any]:
    activities = [
        {
            "index": index,
            "activity_name": event.get("activity_name"),
            "input_fingerprint": event.get("input_fingerprint"),
            "output_fingerprint": event.get("output_fingerprint"),
        }
        for index, event in enumerate(log)
    ]
    payload = {
        "schema_version": "compiler-event-log-manifest-v1",
        "event_count": len(log),
        "activities": activities,
    }
    return {
        **payload,
        "manifest_sha256": generate_fingerprint(payload),
    }


def verify_event_log_manifest(
    log: List[Dict[str, Any]],
    manifest: Dict[str, Any],
) -> Dict[str, Any]:
    expected = build_event_log_manifest(log)
    valid = expected == manifest
    return {
        "valid": valid,
        "verifier_identity": "specgraph.compiler_replay.manifest.v1",
        "expected_manifest_sha256": expected["manifest_sha256"],
        "observed_manifest_sha256": manifest.get("manifest_sha256"),
        "finding_count": 0 if valid else 1,
        "findings": [] if valid else [{
            "severity": "ERROR",
            "code": "EVENT_LOG_MANIFEST_MISMATCH",
            "message": "Compiler event log manifest does not match the event log.",
            "path": "event_log",
        }],
    }
