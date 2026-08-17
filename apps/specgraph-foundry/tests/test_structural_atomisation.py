"""Extraction that does not depend on the document speaking in modal verbs.

Every case here produced zero atoms before. A document is not obliged to say
"the system shall"; most say what they want in a list, a table, or a declared
id, and a compiler that hears only modals cannot read them.
"""

import unittest

from specgraph_foundry.compiler import SpecGraphCompiler
from specgraph_foundry.compiler.format_adapters import (
    repair_wrapped_text,
    wrap_damage_ratio,
)
from specgraph_foundry.compiler.verb_lexicon import (
    is_action_verb,
    lexicon_fingerprint,
    verb_count,
)


class TestStructuralAdmission(unittest.TestCase):
    def setUp(self):
        self.compiler = SpecGraphCompiler("test")

    def compile(self, doc):
        return self.compiler.compile("t.md", doc.encode("utf-8"))

    def test_a_plain_bullet_list_yields_one_atom_per_bullet(self):
        # The case that motivated all of this: three obvious deliverables,
        # no modal verb anywhere, and the compiler returned nothing at all.
        doc = "# Deliverables\n\n- Dark mode toggle\n- Offline sync\n- CSV export\n"

        result = self.compile(doc)

        self.assertEqual(len(result["requirements"]), 3)

    def test_a_bullet_states_no_force_it_did_not_claim(self):
        doc = "# Deliverables\n\n- Dark mode toggle\n"

        force = self.compile(doc)["requirements"][0]["force"]

        # Admission is not obligation. Reading MUST into a bullet that never
        # said it would be inventing an obligation on the operator's behalf.
        self.assertNotIn(force, {"MUST", "SHALL", "MUST_NOT"})

    def test_prose_without_structure_is_still_left_alone(self):
        # The abstention that must survive: a paragraph naming no obligation
        # and sitting in no structure has nothing to point at.
        doc = "This document describes the background of the project.\n"

        self.assertEqual(len(self.compile(doc)["requirements"]), 0)

    def test_a_declared_id_is_admitted_with_no_modal_present(self):
        doc = "S-001 · Six-answers status contract\nIMPL: One typed model in the shared module.\n"

        result = self.compile(doc)

        self.assertGreaterEqual(len(result["requirements"]), 1)

    def test_a_letter_suffixed_id_is_a_declaration_too(self):
        # `B-MCP-GH-a` ends in a letter, not a number. A digits-only pattern
        # missed fifty-nine ids in one real document.
        doc = "B-MCP-GH-a · Open a pull request · estLOC 30\n"

        self.assertGreaterEqual(len(self.compile(doc)["requirements"]), 1)


class TestVerbSplitting(unittest.TestCase):
    """The source rule: one atom = one action."""

    def setUp(self):
        self.compiler = SpecGraphCompiler("test")

    def test_two_actions_joined_by_and_become_two_atoms(self):
        doc = "# Work\n\n- Parse the manifest and publish the result\n"

        result = self.compiler.compile("t.md", doc.encode("utf-8"))

        self.assertEqual(len(result["requirements"]), 2)

    def test_one_action_stays_one_atom(self):
        doc = "# Work\n\n- Parse the manifest for the build\n"

        self.assertEqual(len(self.compiler.compile("t.md", doc.encode("utf-8"))["requirements"]), 1)

    def test_an_and_joining_two_nouns_is_not_a_split(self):
        # "config and secrets" is one action over two objects. Splitting there
        # would produce two halves of a single requirement, which is worse than
        # leaving it whole.
        doc = "# Work\n\n- Read the config and the secrets\n"

        self.assertEqual(len(self.compiler.compile("t.md", doc.encode("utf-8"))["requirements"]), 1)


class TestVerbLexicon(unittest.TestCase):
    def test_inflected_forms_resolve_to_their_stem(self):
        for word in ("render", "renders", "rendered", "rendering"):
            self.assertTrue(is_action_verb(word), word)

    def test_y_and_e_stems_resolve(self):
        self.assertTrue(is_action_verb("applies"))
        self.assertTrue(is_action_verb("cached"))

    def test_copulas_are_not_actions(self):
        # Counting "is" would split nearly every clause in a specification.
        for word in ("is", "are", "was", "be", "the", "system"):
            self.assertFalse(is_action_verb(word), word)

    def test_counting_is_positional_and_repeatable(self):
        self.assertEqual(verb_count("Parse the manifest and publish the result"), 2)
        self.assertEqual(verb_count("The panel is visible"), 0)

    def test_the_lexicon_is_fingerprinted(self):
        # A run records which vocabulary produced its atoms; the same
        # vocabulary must hash the same on every machine and every run.
        self.assertRegex(lexicon_fingerprint(), r"^[0-9a-f]{16}$")
        self.assertEqual(lexicon_fingerprint(), lexicon_fingerprint())


class TestWrapDamage(unittest.TestCase):
    def test_one_word_per_line_is_detected(self):
        damaged = "Read\nthe\nmanifest\nfor\nthe\nbuild\n"

        self.assertGreater(wrap_damage_ratio(damaged), 0.9)

    def test_ordinary_prose_is_not_flagged(self):
        healthy = "The engine reads the manifest.\nIt then publishes a result.\n"

        self.assertLess(wrap_damage_ratio(healthy), 0.3)

    def test_repair_rejoins_a_shredded_line(self):
        self.assertEqual(
            repair_wrapped_text("Read the manifest\nfor\nthe\nbuild\n").strip(),
            "Read the manifest for the build",
        )

    def test_repair_leaves_structural_openers_alone(self):
        # A genuine one-word bullet is not damage, and folding it into the line
        # above would silently destroy a declared item.
        text = "- alpha\n- beta\n- gamma\n"

        self.assertEqual(repair_wrapped_text(text), text)


if __name__ == "__main__":
    unittest.main()
