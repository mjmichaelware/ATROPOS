import unittest
from specgraph_foundry.compiler.structural_validation import StructuralValidator, ValidationFinding
from specgraph_foundry.compiler.document_ir import DocumentNode, generate_stable_id, STRUCTURAL_ROLES
from specgraph_foundry.compiler.source_coordinates import SourceCoordinates
from specgraph_foundry.compiler import SpecGraphCompiler


class TestStructuralValidatorDirect(unittest.TestCase):
    def setUp(self):
        self.source_sha256 = "test-source-sha256"

    def _make_node(self, node_id: str, role: str, content: str,
                   byte_start: int, byte_end: int,
                   line_start: int = 1, line_end: int = 1,
                   parent_id: str = None) -> DocumentNode:
        coords = SourceCoordinates(
            byte_start=byte_start, byte_end=byte_end,
            line_start=line_start, line_end=line_end,
        )
        return DocumentNode(node_id, role, content, coords, parent_id=parent_id)

    def _make_root(self) -> DocumentNode:
        return self._make_node("root", "DOCUMENT", "", 0, 100, 1, 10)

    def test_valid_tree_no_findings(self):
        root = self._make_root()
        child_a = self._make_node("n1", "HEADING", "Title", 0, 20, 1, 2, "root")
        child_b = self._make_node("n2", "PARAGRAPH", "Body", 21, 100, 3, 10, "root")
        root.children = [child_a, child_b]

        validator = StructuralValidator(self.source_sha256)
        result = validator.validate(root)

        self.assertEqual(len(result.findings), 0)
        self.assertEqual(len(result.accepted), 3)
        self.assertEqual(len(result.quarantined), 0)

    def test_overlapping_siblings_generates_finding(self):
        root = self._make_root()
        child_a = self._make_node("n1", "PARAGRAPH", "First", 0, 50, 1, 3, "root")
        child_b = self._make_node("n2", "PARAGRAPH", "Second", 40, 100, 4, 10, "root")
        root.children = [child_a, child_b]

        validator = StructuralValidator(self.source_sha256)
        result = validator.validate(root)

        overlap_findings = [f for f in result.findings if f.code == "OVERLAPPING_REGION"]
        self.assertEqual(len(overlap_findings), 1)
        self.assertIn("n2", overlap_findings[0].node_id)
        self.assertIn("OVERLAPPING_REGION", overlap_findings[0].code)

    def test_invalid_byte_range_quarantines_node(self):
        root = self._make_root()
        bad_node = self._make_node("bad", "PARAGRAPH", "Bad", 50, 25, 2, 3, "root")
        root.children = [bad_node]

        validator = StructuralValidator(self.source_sha256)
        result = validator.validate(root)

        self.assertEqual(len(result.quarantined), 1)
        quarantined_node, findings = result.quarantined[0]
        self.assertEqual(quarantined_node.node_id, "bad")
        codes = {f.code for f in findings}
        self.assertIn("INVALID_BYTE_RANGE", codes)

    def test_negative_line_start_quarantines_node(self):
        root = self._make_root()
        bad_node = self._make_node("bad", "PARAGRAPH", "Bad", 0, 10, 0, 1, "root")
        root.children = [bad_node]

        validator = StructuralValidator(self.source_sha256)
        result = validator.validate(root)

        self.assertEqual(len(result.quarantined), 1)
        codes = {f.code for f in result.findings}
        self.assertIn("NEGATIVE_LINE_START", codes)

    def test_orphan_node_detected(self):
        root = self._make_root()
        orphan = self._make_node("orphan", "PARAGRAPH", "Lost", 0, 50, 2, 5, "nonexistent-parent")
        root.children = [orphan]

        validator = StructuralValidator(self.source_sha256)
        result = validator.validate(root)

        orphan_findings = [f for f in result.findings if f.code == "ORPHAN_NODE"]
        self.assertEqual(len(orphan_findings), 1)
        self.assertIn("nonexistent-parent", orphan_findings[0].message)

    def test_unknown_role_detected(self):
        root = self._make_root()
        unknown = self._make_node("unk", "BOGUS_ROLE", "Something", 0, 50, 2, 5, "root")
        root.children = [unknown]

        validator = StructuralValidator(self.source_sha256)
        result = validator.validate(root)

        role_findings = [f for f in result.findings if f.code == "UNKNOWN_ROLE"]
        self.assertEqual(len(role_findings), 1)
        self.assertIn("BOGUS_ROLE", role_findings[0].message)

    def test_invalid_utf8_content_detected(self):
        root = self._make_node("root", "DOCUMENT", "", 0, 10, 1, 1)
        bad_bytes = b"\xff\xfe\x00\x01"
        node = self._make_node("bad_utf8", "PARAGRAPH", "garbage", 0, 4, 1, 1, "root")
        root.children = [node]

        validator = StructuralValidator(self.source_sha256, raw_content=bad_bytes)
        result = validator.validate(root)

        utf8_findings = [f for f in result.findings if f.code == "INVALID_UTF8"]
        self.assertEqual(len(utf8_findings), 1)

    def test_deterministic_fingerprint(self):
        root = self._make_root()
        child = self._make_node("n1", "PARAGRAPH", "Body", 10, 90, 2, 9, "root")
        root.children = [child]

        v1 = StructuralValidator(self.source_sha256)
        v2 = StructuralValidator(self.source_sha256)

        r1 = v1.validate(root)
        r2 = v2.validate(root)

        self.assertEqual(r1.fingerprint, r2.fingerprint)
        self.assertIsInstance(r1.fingerprint, str)
        self.assertGreater(len(r1.fingerprint), 0)

    def test_accepted_ids_exact_match(self):
        root = self._make_root()
        a = self._make_node("valid_a", "PARAGRAPH", "A", 0, 30, 2, 4, "root")
        b = self._make_node("valid_b", "PARAGRAPH", "B", 31, 100, 5, 10, "root")
        root.children = [a, b]

        validator = StructuralValidator(self.source_sha256)
        result = validator.validate(root)

        self.assertEqual(set(result.accepted_ids), {"root", "valid_a", "valid_b"})
        self.assertEqual(result.quarantined_ids, [])
        self.assertEqual(result.accepted_count, 3)
        self.assertEqual(result.quarantined_count, 0)


class TestStructuralValidationViaCompiler(unittest.TestCase):
    def test_clean_document_produces_no_structural_findings(self):
        doc = "# Clean Spec\nThe system must work.\n"
        compiler = SpecGraphCompiler("test")
        result = compiler.compile("clean.md", doc.encode("utf-8"))

        struct_events = [
            ev for ev in result["event_log"]
            if ev["activity_name"] == "StructuralValidation"
        ]
        self.assertEqual(len(struct_events), 1)
        payload = struct_events[0]["result_payload"]
        self.assertEqual(payload["total_findings"], 0)
        self.assertEqual(payload["quarantined_count"], 0)

    def test_adversarial_overlapping_ranges_quarantine(self):
        doc = "# Overlap\nLine A.\nLine B.\nLine C.\n"
        compiler = SpecGraphCompiler("test")
        result = compiler.compile("overlap.md", doc.encode("utf-8"))

        struct_events = [
            ev for ev in result["event_log"]
            if ev["activity_name"] == "StructuralValidation"
        ]
        self.assertEqual(len(struct_events), 1)
        payload = struct_events[0]["result_payload"]
        for finding in payload["findings"]:
            self.assertIn(finding["code"], {
                "NEGATIVE_BYTE_START", "INVALID_BYTE_RANGE", "NEGATIVE_LINE_START",
                "INVALID_LINE_RANGE", "UNKNOWN_ROLE", "INVALID_UTF8",
                "OVERLAPPING_REGION", "ORPHAN_NODE",
            })
            self.assertIsNotNone(finding["node_id"])

    def test_quarantine_removes_node_from_downstream(self):
        root_id = "will-be-root"
        content = b"ABCDEF"
        root = DocumentNode(
            root_id, "DOCUMENT", "", SourceCoordinates(0, 6, 1, 1),
            children=[]
        )
        bad_node = DocumentNode(
            "bad", "PARAGRAPH", "bad",
            SourceCoordinates(3, 2, 1, 1), parent_id=root_id,
        )
        valid_node = DocumentNode(
            "good", "PARAGRAPH", "good",
            SourceCoordinates(0, 2, 1, 1), parent_id=root_id,
        )
        root.children = [valid_node, bad_node]

        validator = StructuralValidator("sha256", raw_content=content)
        result = validator.validate(root)

        self.assertEqual(result.quarantined_count, 1)
        self.assertIn("bad", result.quarantined_ids)
        self.assertNotIn("bad", result.accepted_ids)
        self.assertIn("good", result.accepted_ids)


if __name__ == "__main__":
    unittest.main()
