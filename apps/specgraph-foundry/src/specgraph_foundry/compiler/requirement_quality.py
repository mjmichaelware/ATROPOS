import re
from typing import List, Dict, Any

VAGUE_TERMS = {
    "easy", "simple", "fast", "efficient", "flexible", "user-friendly", "robust",
    "quickly", "optimal", "adequate", "appropriate", "reasonable", "reliable"
}

WEAK_VERBS = {
    "try to", "attempt", "minimize", "maximize", "optimize", "support", "enable",
    "could", "might", "may"
}

AMBIGUOUS_PRONOUNS = {
    "it", "they", "them", "their", "its"
}

OPEN_ENDED_MARKERS = {
    "etc.", "and so on", "such as", "including but not limited to"
}

def analyze_quality(text: str) -> List[Dict[str, str]]:
    findings = []
    text_lower = text.lower()

    # 1. Check for vague terms
    for term in VAGUE_TERMS:
        if re.search(r"\b" + re.escape(term) + r"\b", text_lower):
            findings.append({
                "severity": "WARNING",
                "code": "VAGUE_TERM",
                "message": f"Statement contains vague/subjective term: '{term}'"
            })

    # 2. Check for weak verbs
    for verb in WEAK_VERBS:
        if re.search(r"\b" + re.escape(verb) + r"\b", text_lower):
            findings.append({
                "severity": "WARNING",
                "code": "WEAK_VERB",
                "message": f"Statement contains weak/non-verifiable verb: '{verb}'"
            })

    # 3. Check for ambiguous pronouns
    for pronoun in AMBIGUOUS_PRONOUNS:
        if re.search(r"\b" + re.escape(pronoun) + r"\b", text_lower):
            findings.append({
                "severity": "WARNING",
                "code": "AMBIGUOUS_PRONOUN",
                "message": f"Statement contains ambiguous pronoun: '{pronoun}'"
            })

    # 4. Check for open-ended lists
    for marker in OPEN_ENDED_MARKERS:
        if marker in text_lower:
            findings.append({
                "severity": "WARNING",
                "code": "OPEN_ENDED_LIST",
                "message": f"Statement contains open-ended list marker: '{marker}'"
            })

    # 5. Check for passive voice
    passive_match = re.search(r"\b(?:is|are|was|were|be|been|being)\s+([a-zA-Z]+ed)\b", text_lower)
    if passive_match:
        findings.append({
            "severity": "WARNING",
            "code": "PASSIVE_VOICE",
            "message": f"Passive voice detected: '{passive_match.group(0)}'. Actor might be missing."
        })

    return findings


class DefectToRemediation:
    def __init__(self, defect_statement: str, defect_coordinates: Dict[str, Any],
                 original_role: str = "DEFECT_FINDING"):
        self.defect_statement = defect_statement
        self.defect_coordinates = defect_coordinates
        self.original_role = original_role

    def convert(self) -> Optional[Dict[str, Any]]:
        defect_lower = self.defect_statement.lower()
        remediation_text = None

        if "defect" in defect_lower or "bug" in defect_lower:
            remediation_text = self._derive_remediation("fix", defect_lower)
        elif "error" in defect_lower or "failure" in defect_lower:
            remediation_text = self._derive_remediation("handle", defect_lower)
        elif "missing" in defect_lower:
            remediation_text = self._derive_remediation("implement", defect_lower)
        elif "empty" in defect_lower or "broken" in defect_lower:
            remediation_text = self._derive_remediation("repair", defect_lower)

        if remediation_text is None:
            return None

        return {
            "remediation_statement": remediation_text,
            "new_role": "REMEDIATION_REQUIREMENT",
            "original_finding": {
                "statement": self.defect_statement,
                "coordinates": self.defect_coordinates,
                "role": self.original_role,
            },
            "conversion_rule": f"DEFECT_TO_REMEDIATION",
        }

    def _derive_remediation(self, action: str, defect_lower: str) -> Optional[str]:
        text = self.defect_statement
        words = text.split()
        if len(words) > 3:
            remainder = " ".join(words[1:])
            return f"The system must {action} {remainder}"
        return f"The system must {action} the identified defect"


def convert_defect_findings(findings: List[Dict[str, Any]],
                            statements: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    remediations = []
    for stmt in statements:
        if stmt.get("role") == "DEFECT_FINDING":
            converter = DefectToRemediation(
                defect_statement=stmt.get("canonical_text", ""),
                defect_coordinates=stmt.get("coordinates", {}),
            )
            result = converter.convert()
            if result:
                remediations.append(result)
    return remediations
