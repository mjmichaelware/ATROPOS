import json
import unittest
from pathlib import Path
from urllib.error import HTTPError

from specgraph_foundry.http_api.storage import (
    StorageObjectMissingError,
    StorageObjectTooLargeError,
    StorageProtocolError,
    SupabaseStorageClient,
)


ROOT = Path(__file__).resolve().parents[1]
DEPLOYMENT = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260712001100_source_uploads.sql"
)
DEPLOYMENT_DERIVATIONS = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260712001200_document_derivations.sql"
)
DEPLOYMENT_DURABLE_ARTIFACTS = (
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
    / "202607120010_source_uploads.sql"
)
SOURCE_DERIVATIONS = (
    ROOT
    / "infra"
    / "supabase"
    / "migrations"
    / "202607120011_document_derivations.sql"
)
SOURCE_DURABLE_ARTIFACTS = (
    ROOT
    / "infra"
    / "supabase"
    / "migrations"
    / "202607120012_durable_artifacts.sql"
)


class FakeResponse:
    def __init__(
        self,
        payload: bytes,
        *,
        url: str,
        headers: dict[str, str] | None = None,
    ) -> None:
        self.payload = payload
        self.url = url
        self.headers = headers or {}

    def __enter__(self):
        return self

    def __exit__(
        self,
        exception_type,
        exception,
        traceback,
    ) -> bool:
        return False

    def read(
        self,
        size: int = -1,
    ) -> bytes:
        if size < 0:
            return self.payload
        return self.payload[:size]

    def geturl(self) -> str:
        return self.url


class StorageSecurityTest(unittest.TestCase):
    def test_source_and_deployment_migrations_match(
        self,
    ) -> None:
        self.assertEqual(
            SOURCE.read_bytes(),
            DEPLOYMENT.read_bytes(),
        )
        self.assertEqual(
            SOURCE_DERIVATIONS.read_bytes(),
            DEPLOYMENT_DERIVATIONS.read_bytes(),
        )
        self.assertEqual(
            SOURCE_DURABLE_ARTIFACTS.read_bytes(),
            DEPLOYMENT_DURABLE_ARTIFACTS.read_bytes(),
        )

    def test_private_bucket_and_owner_policies_are_declared(
        self,
    ) -> None:
        sql = DEPLOYMENT.read_text(
            encoding="utf-8"
        ) + "\n" + DEPLOYMENT_DERIVATIONS.read_text(
            encoding="utf-8"
        ) + "\n" + DEPLOYMENT_DURABLE_ARTIFACTS.read_text(
            encoding="utf-8"
        )
        self.assertIn(
            "'source-documents'",
            sql,
        )
        self.assertIn(
            "'export-artifacts'",
            sql,
        )
        self.assertIn(
            "'application/pdf'",
            sql,
        )
        self.assertIn(
            "'application/vnd.openxmlformats-officedocument.wordprocessingml.document'",
            sql,
        )
        self.assertIn(
            "false",
            sql,
        )
        self.assertIn(
            "on public.source_uploads",
            sql,
        )
        self.assertIn(
            "drop constraint if exists source_uploads_document_id_key",
            DEPLOYMENT_DERIVATIONS.read_text(
                encoding="utf-8"
            ),
        )
        self.assertIn(
            "on public.document_derivations",
            DEPLOYMENT_DERIVATIONS.read_text(
                encoding="utf-8"
            ),
        )
        self.assertIn(
            "on public.storage_objects",
            DEPLOYMENT_DURABLE_ARTIFACTS.read_text(
                encoding="utf-8"
            ),
        )
        self.assertIn(
            "on public.artifact_manifests",
            DEPLOYMENT_DURABLE_ARTIFACTS.read_text(
                encoding="utf-8"
            ),
        )
        self.assertIn(
            "on storage.objects",
            sql,
        )
        self.assertIn(
            "split_part(name, '/', 1)",
            sql,
        )
        self.assertIn(
            "split_part(name, '/', 4) = 'source'",
            sql,
        )
        self.assertIn(
            "array_length(string_to_array(name, '/'), 1) = 4",
            DEPLOYMENT_DURABLE_ARTIFACTS.read_text(
                encoding="utf-8"
            ),
        )
        self.assertNotIn(
            "for delete",
            sql.lower(),
        )
        self.assertNotIn(
            "for update",
            sql.lower(),
        )

    def test_cross_origin_signed_url_is_rejected(
        self,
    ) -> None:
        def opener(request, timeout):
            payload = json.dumps(
                {
                    "url": "https://evil.example/storage/v1/object/upload/source-documents/demo"
                }
            ).encode("utf-8")
            return FakeResponse(
                payload,
                url=request.full_url,
            )

        client = SupabaseStorageClient(
            "https://example.supabase.co",
            "anon-key",
            timeout_seconds=5,
            opener=opener,
        )

        with self.assertRaises(
            StorageProtocolError
        ):
            client.create_signed_upload_target(
                authorization="Bearer valid",
                bucket="source-documents",
                object_path="owner/project/upload/source",
                ttl_seconds=300,
            )

    def test_invalid_json_and_oversized_downloads_are_rejected(
        self,
    ) -> None:
        def bad_json(request, timeout):
            return FakeResponse(
                b"not-json",
                url=request.full_url,
            )

        client = SupabaseStorageClient(
            "https://example.supabase.co",
            "anon-key",
            timeout_seconds=5,
            opener=bad_json,
        )

        with self.assertRaises(
            StorageProtocolError
        ):
            client.create_signed_upload_target(
                authorization="Bearer valid",
                bucket="source-documents",
                object_path="owner/project/upload/source",
                ttl_seconds=300,
            )

        def huge_download(request, timeout):
            return FakeResponse(
                b"x" * 32,
                url=request.full_url,
                headers={"content-type": "text/plain"},
            )

        client = SupabaseStorageClient(
            "https://example.supabase.co",
            "anon-key",
            timeout_seconds=5,
            opener=huge_download,
        )

        with self.assertRaises(
            StorageObjectTooLargeError
        ):
            client.download_object(
                authorization="Bearer valid",
                bucket="source-documents",
                object_path="owner/project/upload/source",
                max_bytes=8,
            )

    def test_missing_object_maps_to_404_boundary(
        self,
    ) -> None:
        def missing(request, timeout):
            raise HTTPError(
                request.full_url,
                404,
                "missing",
                {},
                None,
            )

        client = SupabaseStorageClient(
            "https://example.supabase.co",
            "anon-key",
            timeout_seconds=5,
            opener=missing,
        )

        with self.assertRaises(
            StorageObjectMissingError
        ) as context:
            client.download_object(
                authorization="Bearer valid",
                bucket="source-documents",
                object_path="owner/project/upload/source",
                max_bytes=8,
            )

        self.assertIn(
            "not found",
            str(context.exception).lower(),
        )


if __name__ == "__main__":
    unittest.main()
