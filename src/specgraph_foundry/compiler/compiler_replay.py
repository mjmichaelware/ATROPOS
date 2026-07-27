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
    Simulate compiler replay of activities by checking recorded event transitions.
    """
    if not log:
        return False
    # Validate each transition fingerprint
    for i, event in enumerate(log):
        # The output of event i must match the input of event i+1 (if they form a sequence)
        if i + 1 < len(log):
            if event["output_fingerprint"] != log[i+1]["input_fingerprint"] and log[i+1]["activity_name"] != "Ingest":
                return False

    # Check if final output fingerprint matches the expected final fingerprint
    return log[-1]["output_fingerprint"] == expected_final_fingerprint
