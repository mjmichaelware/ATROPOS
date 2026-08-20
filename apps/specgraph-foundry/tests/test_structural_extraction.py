"""Structures a line-at-a-time classifier could not see.

Three ways of stating work went into the paragraph accumulator and came out as
one rejected blob each: a directory listing, a fixed-width table, and a
`Key: value` line. Measured on three real documents, extraction went from 256
atoms to 1293 -- and the largest single gain was the build specification whose
two directory trees state, literally, every file the project must contain.
"""

import unittest

from specgraph_foundry.compiler import SpecGraphCompiler
from specgraph_foundry.compiler.atomic_decomposition import split_inventory
from specgraph_foundry.compiler.block_structures import detect_blocks
from specgraph_foundry.compiler.format_adapters import (
    parse_markdown_to_ir,
    repair_wrapped_text,
    wrap_damage_ratio,
)

TREE = """Project layout

musicmakerlm/
  README.md
  app/
    main.py
    routes/
      generate.py
      analyze.py
  tests/
    test_generation.py
"""

MATRIX = """Feature            Suno   AIVA   Target

MIDI export        N      Y      Y
Notation           N      N      Y
Offline operation  N      N      Y
"""

PIPE_TABLE = """| Feature | Suno | Target |
| --- | --- | --- |
| MIDI export | N | Y |
| Notation | N | Y |
"""


def _roles(text):
    root = parse_markdown_to_ir("p", "s" * 64, text)
    roles = {}
    for child in root.children:
        roles.setdefault(child.role, []).append(child.content)
    return roles


class TestDirectoryTrees(unittest.TestCase):

    def test_every_line_of_a_tree_becomes_its_own_node(self):
        roles = _roles(TREE)
        self.assertEqual(len(roles.get("FILE_PATH", [])), 9, roles)

    def test_indentation_is_read_as_the_parent_path(self):
        # A bare `generate.py` cannot tell a build where to put it.
        paths = _roles(TREE)["FILE_PATH"]
        self.assertIn("musicmakerlm/app/routes/generate.py", paths)
        self.assertIn("musicmakerlm/tests/test_generation.py", paths)

    def test_a_tree_survives_wrap_damage_repair(self):
        # A directory listing has exactly the shape the repair exists to fix --
        # one token per line -- and repairing it glued every path into a
        # paragraph before the parser saw any of them.
        self.assertLess(wrap_damage_ratio(TREE), 0.30)
        self.assertIn("      generate.py", repair_wrapped_text(TREE))

    def test_a_column_of_words_is_not_a_tree(self):
        # No directory, no indentation: a list of something else.
        blocks = detect_blocks(["alpha", "beta", "gamma", "delta"])
        self.assertEqual(blocks, {})

    def test_paths_reach_the_compiler_as_requirements(self):
        result = SpecGraphCompiler("t").compile("tree.md", TREE.encode())
        statements = [r["canonical_statement"] for r in result["requirements"]]
        self.assertIn("musicmakerlm/app/routes/generate.py", statements)


class TestTables(unittest.TestCase):

    def test_a_fixed_width_matrix_yields_one_row_each(self):
        roles = _roles(MATRIX)
        self.assertEqual(len(roles.get("TABLE_ROW", [])), 3, roles)

    def test_a_row_carries_its_column_names(self):
        # `Y` on its own is not a requirement; the header is what makes it one.
        rows = _roles(MATRIX)["TABLE_ROW"]
        self.assertIn("MIDI export -- Suno: N; AIVA: Y; Target: Y", rows)

    def test_a_pipe_table_is_read_too(self):
        # TABLE_ROW has been declared and handled since the beginning; no
        # parser ever emitted one.
        roles = _roles(PIPE_TABLE)
        self.assertEqual(len(roles.get("TABLE_ROW", [])), 2, roles)

    def test_rows_reach_the_compiler_as_requirements(self):
        result = SpecGraphCompiler("t").compile("m.md", MATRIX.encode())
        self.assertGreaterEqual(len(result["requirements"]), 3)


class TestKeyValueLines(unittest.TestCase):

    def test_a_key_with_its_value_on_the_same_line_is_its_own_node(self):
        roles = _roles("Symbolic core: music21, pretty_midi, mido\n")
        self.assertEqual(roles.get("KEY_VALUE"), ["Symbolic core: music21, pretty_midi, mido"])

    def test_a_document_field_is_not_work(self):
        result = SpecGraphCompiler("t").compile("d.md", b"Status: DRAFT\n")
        self.assertEqual(len(result["requirements"]), 0)

    def test_commentary_is_not_work(self):
        result = SpecGraphCompiler("t").compile("d.md", b"Observation: The system processes data.\n")
        self.assertEqual(len(result["requirements"]), 0)

    def test_an_outstanding_work_marker_is_a_defect_not_a_requirement(self):
        result = SpecGraphCompiler("t").compile("d.md", b"Todo: Add error handling for null values.\n")
        self.assertEqual(len(result["requirements"]), 0)

    def test_a_long_lead_in_is_prose_rather_than_a_key(self):
        roles = _roles(
            "The addressable market expands when notation software is included: "
            "notation alone is $800M a year.\n"
        )
        self.assertNotIn("KEY_VALUE", roles)


class TestInventorySplitting(unittest.TestCase):

    def test_a_semicolon_inventory_becomes_one_atom_per_item(self):
        items = split_inventory(
            "From Suno's inability to edit: piano roll editor; "
            "chord symbol click-to-change; velocity slider per note"
        )
        self.assertEqual(len(items), 3)
        self.assertTrue(all(item.startswith("From Suno's inability to edit: ") for item in items), items)

    def test_a_comma_inventory_splits_too(self):
        items = split_inventory("Dev tooling: pytest, black, ruff, mypy")
        self.assertEqual(len(items), 4)

    def test_a_trailing_conjunction_is_not_part_of_the_item(self):
        items = split_inventory("Formats: MIDI, MusicXML, PDF, and MP3")
        self.assertIn("Formats: MP3", items)

    def test_prose_with_commas_is_left_whole(self):
        # The guard that keeps this from shattering ordinary sentences.
        self.assertEqual(split_inventory("The system, which is fast, handles input."), [])

    def test_two_items_are_not_an_inventory(self):
        self.assertEqual(split_inventory("Formats: MIDI, MusicXML"), [])


if __name__ == "__main__":
    unittest.main()
