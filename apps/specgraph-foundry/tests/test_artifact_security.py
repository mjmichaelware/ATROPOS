import unittest
from pathlib import Path

from specgraph_foundry.errors import ValidationError
from specgraph_foundry.http_api.artifact_storage import (
    ArtifactStorageSettings,
    artifact_object_path,
    validate_artifact_name,
    validate_segment,
)


ROOT = Path(__file__).resolve().parents[1]
DEPLOYMENT = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260712001300_durable_artifacts.sql"
)
SOURCE = (
    ROOT
    / "infra"
    / "supabase"
    / "migrations"
    / "202607120012_durable_artifacts.sql"
)


class ArtifactSecurityTest(unittest.TestCase):
    def test_source_and_deployment_migrations_match(self) -> None:
        self.assertEqual(
            SOURCE.read_bytes(),
            DEPLOYMENT.read_bytes(),
        )

    def test_private_bucket_and_owner_scoped_policies(self) -> None:
        sql = DEPLOYMENT.read_text(encoding="utf-8")
        self.assertIn("'export-artifacts'", sql)
        self.assertIn("false", sql)
        self.assertIn("on public.storage_objects", sql)
        self.assertIn("on public.artifact_manifests", sql)
        self.assertIn("enable row level security", sql)
        self.assertIn("split_part(name, '/', 1)", sql)
        self.assertIn("array_length(string_to_array(name, '/'), 1) = 4", sql)
        self.assertNotIn("for update", sql.lower())
        self.assertNotIn("for delete", sql.lower())
        self.assertNotRegex(sql.lower(), r"create policy[\s\S]*?to anon")

    def test_path_validation_rejects_traversal_and_controls(self) -> None:
        for value in ("", ".", "..", "a/b", "a\\b", "bad\nname"):
            with self.assertRaises(ValidationError):
                validate_segment(value)
        for name in ("../manifest.json", "nested/manifest.json", "evil.txt"):
            with self.assertRaises(ValidationError):
                validate_artifact_name(name)

    def test_object_path_is_owner_first_and_canonical(self) -> None:
        path = artifact_object_path(
            owner_id="owner-123",
            project_id="project-123",
            export_id="export-123",
            artifact_name="manifest.json",
        )
        self.assertEqual(
            path,
            "owner-123/project-123/export-123/manifest.json",
        )

    def test_ttl_and_size_are_bounded(self) -> None:
        ArtifactStorageSettings(
            bucket="export-artifacts",
            max_artifact_bytes=1024,
            download_ttl_seconds=300,
        )
        with self.assertRaises(ValueError):
            ArtifactStorageSettings(
                bucket="export-artifacts",
                max_artifact_bytes=0,
                download_ttl_seconds=300,
            )
        with self.assertRaises(ValueError):
            ArtifactStorageSettings(
                bucket="export-artifacts",
                max_artifact_bytes=1024,
                download_ttl_seconds=901,
            )


if __name__ == "__main__":
    unittest.main()
