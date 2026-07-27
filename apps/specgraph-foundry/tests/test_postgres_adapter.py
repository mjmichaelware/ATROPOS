import os
import re
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

from specgraph_foundry import atoms
from specgraph_foundry import execution
from specgraph_foundry import exports
from specgraph_foundry import ingestion
from specgraph_foundry import planning
from specgraph_foundry import research
from specgraph_foundry import routing
from specgraph_foundry import services
from specgraph_foundry.config import Settings
from specgraph_foundry.database import (
    Database,
    translate_qmark_sql,
)


ROOT = Path(__file__).resolve().parents[1]


class PostgresAdapterTest(unittest.TestCase):
    def test_qmark_translation_ignores_quotes(
        self,
    ) -> None:
        sql = (
            "SELECT '?' AS literal "
            "FROM projects WHERE id = ? "
            'AND "?" = "?"'
        )

        translated = translate_qmark_sql(sql)

        self.assertEqual(
            translated,
            (
                "SELECT '?' AS literal "
                "FROM projects WHERE id = %s "
                'AND "?" = "?"'
            ),
        )

    def test_environment_selects_postgres(
        self,
    ) -> None:
        with patch.dict(
            os.environ,
            {
                "SPECGRAPH_DATABASE_URL": (
                    "postgresql://example.invalid/db"
                ),
                "SPECGRAPH_OWNER_ID": (
                    "11111111-1111-1111-"
                    "1111-111111111111"
                ),
            },
            clear=False,
        ):
            settings = (
                Settings.from_environment()
            )

        database = Database(
            settings.database_path,
            database_url=(
                settings.database_url
            ),
            owner_id=(
                settings.database_owner_id
            ),
        )

        self.assertTrue(
            database.is_postgres
        )
        self.assertEqual(
            database.backend,
            "postgresql",
        )

    def test_all_new_ids_are_postgres_uuids(
        self,
    ) -> None:
        modules = [
            atoms,
            execution,
            exports,
            ingestion,
            planning,
            research,
            routing,
            services,
        ]

        for module in modules:
            generated = module.new_id(
                "ignored"
            )

            self.assertEqual(
                str(
                    uuid.UUID(generated)
                ),
                generated,
            )

    def test_runtime_sql_has_no_unsupported_forms(
        self,
    ) -> None:
        forbidden = {
            "INSERT OR REPLACE",
            "INSERT OR IGNORE",
            "REPLACE INTO",
            "AUTOINCREMENT",
            "LAST_INSERT_ROWID",
        }

        failures = []

        for path in (
            ROOT
            / "src/specgraph_foundry"
        ).glob("*.py"):
            if path.name == "database.py":
                continue

            content = path.read_text(
                encoding="utf-8"
            ).upper()

            for token in forbidden:
                if token in content:
                    failures.append(
                        f"{path.name}: {token}"
                    )

        self.assertEqual(
            failures,
            [],
        )

    def test_boolean_parameters_are_not_int_cast(
        self,
    ) -> None:
        pattern = re.compile(
            r"int\("
            r"(?:enforce_acyclic|inferred|"
            r"allow_open_research|enabled|"
            r"allow_offline_degraded|"
            r"paid_emergency_enabled|"
            r"complete|all_complete)"
            r"\)"
        )

        failures = []

        for path in (
            ROOT
            / "src/specgraph_foundry"
        ).glob("*.py"):
            content = path.read_text(
                encoding="utf-8"
            )

            if pattern.search(content):
                failures.append(path.name)

        self.assertEqual(
            failures,
            [],
        )


if __name__ == "__main__":
    unittest.main()
