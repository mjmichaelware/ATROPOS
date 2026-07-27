import hashlib


def compute_sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


GOLD_CORPUS_VERSION = "specgraph-gold-v1"

FIXTURES = {}


def register(name: str, content: str, labels: dict):
    raw = content.encode("utf-8")
    FIXTURES[name] = {
        "name": name,
        "raw_bytes": raw,
        "sha256": compute_sha256(raw),
        "labels": labels,
    }


register(
    "prose_heavy_spec",
    "# Data Processing Specification\n\nThe system must process incoming records.\n"
    "Records are received via the API endpoint.\n"
    "The system must validate each record before storage.\n"
    "Invalid records must be quarantined with error details.\n"
    "The system should log all processing steps.\n",
    {
        "expected_requirements": 3,
        "excluded_roles": ["HEADING"],
        "expected_forces": ["MUST", "MUST", "MUST", "SHOULD"],
        "expected_actors": ["system"],
        "corpus_type": "prose-heavy",
        "structural_false_promotions": 0,
    },
)

register(
    "ears_style",
    "# EARS Specification\n"
    "When a record arrives, the system must validate it.\n"
    "While processing, the system should log metrics.\n"
    "The system must never accept invalid input.\n",
    {
        "expected_requirements": 3,
        "ears_patterns": ["Event-driven", "State-driven", "Ubiquitous"],
        "corpus_type": "ears",
        "structural_false_promotions": 0,
    },
)

register(
    "mixed_rationale_requirements",
    "# Rationale and Requirements\n"
    "Rationale: The system needs encryption to protect data at rest.\n"
    "The system must encrypt all stored credentials.\n"
    "Example: The encryption uses AES-256.\n"
    "The system must rotate keys every 90 days.\n",
    {
        "expected_requirements": 2,
        "excluded_roles": ["HEADING", "RATIONALE", "EXAMPLE"],
        "expected_forces": ["MUST", "MUST"],
        "structural_false_promotions": 0,
    },
)

register(
    "conflicting_requirements",
    "# Conflicting Spec\n"
    "The system must allow public access.\n"
    "The system must not allow public access.\n",
    {
        "expected_requirements": 2,
        "expected_conflicts": 1,
        "conflict_types": ["CONFLICTS_WITH"],
        "structural_false_promotions": 0,
    },
)

register(
    "producer_consumer",
    "# Producer Consumer Spec\n"
    "The database service must produce schema-records.\n"
    "The API service must consume schema-records.\n"
    "The UI service should render schema-records.\n",
    {
        "expected_requirements": 3,
        "expected_dependencies": 2,
        "structural_false_promotions": 0,
    },
)

register(
    "nested_list_normative",
    "# List Spec\n"
    "The system MUST support:\n"
    "- deterministic output\n"
    "- exact provenance\n"
    "- verifiable results\n"
    "This paragraph after the list is not normative.\n",
    {
        "expected_requirements": 4,
        "inherited_count": 3,
        "structural_false_promotions": 0,
    },
)

register(
    "ambiguity_defect_findings",
    "# Defect Report\n"
    "Defect: The system fails to validate empty inputs.\n"
    "The system must handle empty inputs gracefully.\n"
    "Todo: Add error handling for null values.\n",
    {
        "expected_requirements": 1,
        "excluded_roles": ["HEADING", "DEFECT_FINDING"],
        "expected_remediations": 1,
        "structural_false_promotions": 0,
    },
)

register(
    "unicode_edge_cases",
    "# Unicode Spec\n"
    "Le système doit traiter les caractères Unicode.\n"
    "システムはデータを保存する必要があります。\n"
    "The system must handle Emoji 😊 in data.\n",
    {
        "expected_requirements": 1,
        "structural_false_promotions": 0,
    },
)

register(
    "source_document_3_adversarial",
    "# Source Document 3\n"
    "Inputs:\n"
    "Source Document #3:\n"
    "__PART A__\n"
    "The system MUST provide:\n"
    "- deterministic output\n"
    "- exact provenance\n"
    "This explaining text should generally not be promoted.\n"
    "Example: We show why systems should have encryption.\n"
    "NOTE: This is a background note.\n"
    "WARNING: Modal words must not force warning promotion.\n"
    "Status: DRAFT\n"
    "__PART B__\n"
    "The UI screen should consume schema-records.\n",
    {
        "expected_requirements": 4,
        "structural_false_promotions": 0,
        "corpus_type": "adversarial",
        "description": "ATROPOS Source Document 3 patterns - labels, separators, examples, notes, warnings must produce zero executable nodes",
    },
)


CORPUS_MANIFEST = {
    "version": GOLD_CORPUS_VERSION,
    "fixture_count": len(FIXTURES),
    "fixtures": list(FIXTURES.keys()),
    "fingerprint": compute_sha256(
        str({k: v["sha256"] for k, v in FIXTURES.items()}).encode()
    ),
}
