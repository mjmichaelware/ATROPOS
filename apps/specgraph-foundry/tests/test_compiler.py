import unittest
from specgraph_foundry.compiler import SpecGraphCompiler

class CompilerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.compiler = SpecGraphCompiler(project_id="test-project")
    def test_markdown_compilation(self) -> None:
        doc = (
            "# System Specification\n"
            "## Database Section\n"
            "The database service must produce schema-records.\n"
            "The database service must write to the database-schema.\n"
            "__PART B__\n"
            "The UI screen should consume schema-records.\n"
        )
        result = self.compiler.compile("spec.md", doc.encode("utf-8"))

        # Check requirements count (should extract 3 executable requirements)
        requirements = result["requirements"]
        self.assertEqual(len(requirements), 3)

        # The first requirement is: The database service must produce schema-records.
        # Modality: MUST, Domain: DATA/FUNCTIONAL
        self.assertEqual(requirements[0]["force"], "MUST")

        # The third requirement is: The UI screen should consume schema-records.
        # Modality: SHOULD, Domain: UI_UX
        self.assertEqual(requirements[2]["force"], "SHOULD")
        self.assertIn("UI_UX", requirements[2]["domains"])

        # Check dependencies compiled
        dependencies = result["dependencies"]
        self.assertGreater(len(dependencies), 0)

        # Check that separator '__PART B__' did not generate any requirement
        statements = [r["canonical_statement"] for r in requirements]
        self.assertNotIn("__PART B__", statements)

    def test_structural_exclusions(self) -> None:
        doc = (
            "# System Title\n"
            "Source Document #3:\n"
            "Inputs:\n"
            "The system MUST provide:\n"
            "- deterministic output\n"
            "- exact provenance\n"
            "This explaining text should generally not be promoted.\n"
            "Example: We show why systems should have encryption.\n"
            "NOTE: This is a background note.\n"
            "WARNING: Modal words must not force warning promotion.\n"
            "Status: DRAFT\n"
        )
        result = self.compiler.compile("exclusions.md", doc.encode("utf-8"))
        requirements = result["requirements"]
        statements = [r["canonical_statement"] for r in requirements]

        # Verify headings, labels, metadata, prose with modals are excluded
        self.assertNotIn("System Title", statements)
        self.assertNotIn("Source Document #3:", statements)
        self.assertNotIn("Inputs:", statements)
        self.assertNotIn("This explaining text should generally not be promoted.", statements)
        self.assertNotIn("Example: We show why systems should have encryption.", statements)
        self.assertNotIn("NOTE: This is a background note.", statements)
        self.assertNotIn("WARNING: Modal words must not force warning promotion.", statements)
        self.assertNotIn("Status: DRAFT", statements)

        # Normative parent and its list item children must be promoted
        self.assertIn("The system MUST provide:", statements)
        self.assertIn("deterministic output", statements)
        self.assertIn("exact provenance", statements)

    def test_list_context_boundaries(self) -> None:
        doc = (
            "The system SHOULD support:\n"
            "- item A\n"
            "- item B\n"
            "This non-list paragraph resets inheritance.\n"
            "- item C (should not inherit since context was reset)\n"
            "Example: These are example list items:\n"
            "- item D\n"
            "- item E\n"
        )
        result = self.compiler.compile("boundaries.md", doc.encode("utf-8"))
        requirements = result["requirements"]
        statements = [r["canonical_statement"] for r in requirements]

        # Verifies item A and B inherited SHOULD
        item_a = next(r for r in requirements if "item A" in r["canonical_statement"])
        self.assertEqual(item_a["force"], "SHOULD")

        # Item C is still a declared unit and is still extracted. What the
        # boundary protects is force: the reset means it cannot carry the
        # SHOULD from the introducing line.
        item_c = next(
            r for r in requirements
            if "item C (should not inherit since context was reset)" in r["canonical_statement"]
        )
        self.assertNotEqual(item_c["force"], "SHOULD")

        # Items D and E sit under an Example introduction. They are admitted on
        # structure like any other item, and must not have inherited a force
        # either -- an example is not an obligation.
        for name in ("item D", "item E"):
            item = next(r for r in requirements if name in r["canonical_statement"])
            self.assertNotIn(item["force"], {"MUST", "SHALL", "SHOULD"})

    def test_candidacy_and_abstention_rejection_reasons(self) -> None:
        doc = (
            "This is just background explanatory prose.\n"
            "The dog must bark.\n"
            "The system MUST store the files.\n"
        )
        result = self.compiler.compile("candidacy_rejections.md", doc.encode("utf-8"))

        # Verify the event log contains the rejection reasons
        candidacy_events = [
            item for item in result["event_log"]
            if item["activity_name"] == "RequirementCandidacy"
        ]
        self.assertEqual(len(candidacy_events), 1)
        candidacy_payload = candidacy_events[0]["result_payload"]["candidacies"]

        # Check that we have 3 statements logged in the candidacy event
        self.assertEqual(len(candidacy_payload), 3)

        # Statement 1 is background prose (non-executable role)
        stmt_1 = next(
            c for c in candidacy_payload
            if "explanatory prose" in c["statement"]["exact_quote"]
        )
        self.assertFalse(stmt_1["is_candidate"])
        self.assertIn("not executable", stmt_1["rejection_reason"])

        # Statement 2 contains a modal 'must' but lacks a valid system actor
        stmt_2 = next(
            c for c in candidacy_payload
            if "The dog must bark." in c["statement"]["exact_quote"]
        )
        self.assertFalse(stmt_2["is_candidate"])
        self.assertIn("lacks a valid system/architectural actor", stmt_2["rejection_reason"])

        # Statement 3 is a valid candidate
        stmt_3 = next(
            c for c in candidacy_payload
            if "MUST store the files" in c["statement"]["exact_quote"]
        )
        self.assertTrue(stmt_3["is_candidate"])
        self.assertIsNone(stmt_3["rejection_reason"])

if __name__ == "__main__":
    unittest.main()
