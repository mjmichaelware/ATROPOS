import json
import unittest
import uuid
from datetime import UTC, datetime
from decimal import Decimal
from pathlib import Path

from specgraph_foundry.database import (
    PostgresRow,
    adapt_postgres_parameters,
    normalize_postgres_value,
    postgres_json_parameter_indexes,
)


ROOT = Path(__file__).resolve().parents[1]


class FakeJson:
    def __init__(
        self,
        value: object,
    ) -> None:
        self.value = value


class HostedReleaseAuditTest(
    unittest.TestCase
):
    def test_json_insert_parameter_mapping(
        self,
    ) -> None:
        sql = """
        INSERT INTO execution_events(
            id,
            run_id,
            payload_json,
            created_at
        )
        VALUES(?,?,?,?)
        """

        self.assertEqual(
            postgres_json_parameter_indexes(
                sql
            ),
            {2},
        )

        identifier = str(
            uuid.uuid4()
        )

        parameters = (
            identifier,
            str(uuid.uuid4()),
            '{"valid":true}',
            (
                "2026-07-12T12:00:00"
                "+00:00"
            ),
        )

        adapted = (
            adapt_postgres_parameters(
                sql,
                parameters,
                FakeJson,
            )
        )

        self.assertIsInstance(
            adapted[0],
            uuid.UUID,
        )
        self.assertIsInstance(
            adapted[1],
            uuid.UUID,
        )
        self.assertIsInstance(
            adapted[2],
            FakeJson,
        )
        self.assertEqual(
            adapted[2].value,
            {"valid": True},
        )
        self.assertIsInstance(
            adapted[3],
            datetime,
        )

    def test_json_update_parameter_mapping(
        self,
    ) -> None:
        sql = """
        UPDATE research_tasks
        SET status = ?,
            result_json = ?,
            updated_at = ?
        WHERE id = ?
        """

        self.assertEqual(
            postgres_json_parameter_indexes(
                sql
            ),
            {1},
        )

    def test_postgres_values_normalize(
        self,
    ) -> None:
        identifier = uuid.uuid4()
        timestamp = datetime.now(UTC)

        self.assertEqual(
            normalize_postgres_value(
                identifier
            ),
            str(identifier),
        )

        self.assertEqual(
            normalize_postgres_value(
                timestamp
            ),
            timestamp.isoformat(),
        )

        self.assertEqual(
            normalize_postgres_value(
                Decimal("7")
            ),
            7,
        )

        encoded = (
            normalize_postgres_value(
                {"a": 1}
            )
        )

        self.assertEqual(
            json.loads(str(encoded)),
            {"a": 1},
        )

    def test_postgres_row_supports_indexes(
        self,
    ) -> None:
        row = PostgresRow(
            {
                "first": "a",
                "second": "b",
            }
        )

        self.assertEqual(
            row["first"],
            "a",
        )
        self.assertEqual(
            row[0],
            "a",
        )
        self.assertEqual(
            row[1],
            "b",
        )

    def test_audit_covers_release_gates(
        self,
    ) -> None:
        content = (
            ROOT
            / "scripts/"
            "hosted_release_audit.py"
        ).read_text(
            encoding="utf-8"
        )

        for required in (
            "verify_rls",
            "NO_EMPTY_IMPLEMENTATION",
            "NO_SELF_VERIFICATION",
            "EVIDENCE_HASH_MISMATCH",
            "ATROPOS",
            "verify_export",
            "verify_plan",
            "resolve_research",
        ):
            self.assertIn(
                required,
                content,
            )


if __name__ == "__main__":
    unittest.main()
