import io
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
DEPLOYMENT_EXPORT_ARTIFACTS_PDF_MIME = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260712001600_export_artifacts_pdf_mime.sql"
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
    def test_canonical_migrations_are_present(
        self,
    ) -> None:
        for path in (
            DEPLOYMENT,
            DEPLOYMENT_DERIVATIONS,
            DEPLOYMENT_DURABLE_ARTIFACTS,
            DEPLOYMENT_EXPORT_ARTIFACTS_PDF_MIME,
        ):
            self.assertTrue(path.is_file())
            self.assertGreater(path.stat().st_size, 0)

    def test_export_artifacts_bucket_allows_pdf_uploads(
        self,
    ) -> None:
        # implementation_blueprint.pdf is uploaded to the export-artifacts
        # bucket with Content-Type: application/pdf - Supabase Storage
        # rejects any upload whose MIME type isn't in the bucket's
        # allowed_mime_types, so every export would fail at the storage
        # step (never reaching VERIFIED) without this.
        sql = DEPLOYMENT_EXPORT_ARTIFACTS_PDF_MIME.read_text(
            encoding="utf-8"
        )
        self.assertIn("'export-artifacts'", sql)
        self.assertIn("'application/pdf'", sql)

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

    def test_relative_signing_response_matches_real_supabase_shape(
        self,
    ) -> None:
        # Supabase's actual storage-api returns {"url": "/object/upload/sign/..."} -
        # relative to the storage API root, not an absolute URL. The official
        # SDKs reconstruct it via string concatenation of
        # `${storageApiUrl}${data.url}` where storageApiUrl already ends in
        # "/storage/v1". This is a regression test for exactly that shape,
        # not the full-absolute-URL shape the other fixtures in this file use.
        def opener(request, timeout):
            payload = json.dumps(
                {
                    "url": "/object/upload/sign/source-documents/owner/project/upload/source?token=signed"
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

        target = client.create_signed_upload_target(
            authorization="Bearer valid",
            bucket="source-documents",
            object_path="owner/project/upload/source",
            ttl_seconds=300,
        )

        self.assertEqual(
            target.url,
            "https://example.supabase.co/storage/v1/object/upload/sign/source-documents/owner/project/upload/source?token=signed",
        )

    def test_signed_download_forces_attachment_disposition_when_requested(
        self,
    ) -> None:
        # Without Supabase's "download" flag in the sign request body,
        # the resulting URL serves the object with no Content-Disposition,
        # so a browser opens text/PDF artifacts inline (a new tab, or the
        # OS PDF viewer) instead of saving them to the device - on mobile
        # that means an extra manual "save" step rather than a direct
        # download. This is a regression test that the request body
        # actually carries that flag when force_download=True, and
        # doesn't when the caller hasn't opted in.
        captured_payloads: list[dict[str, object]] = []

        def opener(request, timeout):
            captured_payloads.append(
                json.loads(request.data.decode("utf-8"))
            )
            payload = json.dumps(
                {
                    "signedURL": (
                        "https://example.supabase.co/storage/v1/object/sign/"
                        "export-artifacts/owner/project/export/implementation_blueprint.pdf?token=signed"
                    )
                }
            ).encode("utf-8")
            return FakeResponse(payload, url=request.full_url)

        client = SupabaseStorageClient(
            "https://example.supabase.co",
            "anon-key",
            timeout_seconds=5,
            opener=opener,
        )

        client.create_signed_download_target(
            authorization="Bearer valid",
            bucket="export-artifacts",
            object_path="owner/project/export/implementation_blueprint.pdf",
            ttl_seconds=300,
            force_download=True,
        )
        client.create_signed_download_target(
            authorization="Bearer valid",
            bucket="export-artifacts",
            object_path="owner/project/export/implementation_blueprint.pdf",
            ttl_seconds=300,
        )

        self.assertEqual(captured_payloads[0].get("download"), True)
        self.assertNotIn("download", captured_payloads[1])

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

    def test_disguised_404_from_supabase_maps_to_missing_object(
        self,
    ) -> None:
        # Supabase Storage reports a missing S3 object as HTTP 400 (not
        # 404), with the real status only visible in the JSON body:
        # {"statusCode": "404", "error": "Not found", "message": "..."}.
        # This is a regression test for that disguised shape.
        def disguised_not_found(request, timeout):
            body = json.dumps(
                {
                    "statusCode": "404",
                    "error": "Not found",
                    "message": "The resource was not found",
                }
            ).encode("utf-8")
            raise HTTPError(
                request.full_url,
                400,
                "Bad Request",
                {},
                io.BytesIO(body),
            )

        client = SupabaseStorageClient(
            "https://example.supabase.co",
            "anon-key",
            timeout_seconds=5,
            opener=disguised_not_found,
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

    def test_download_object_uses_the_real_authenticated_route(
        self,
    ) -> None:
        # Supabase Storage's authenticated GET download route is
        # "/object/{bucket}/{path}" with RLS enforced entirely via the
        # Authorization header - matching the official storage-js SDK's
        # `.download()` implementation. There is no "/object/authenticated/"
        # route; requesting it returns a 400 from real Supabase Storage,
        # which is exactly the bug this test guards against.
        captured_urls: list[str] = []

        def opener(request, timeout):
            captured_urls.append(request.full_url)
            return FakeResponse(
                b"document bytes",
                url=request.full_url,
                headers={"content-type": "text/plain"},
            )

        client = SupabaseStorageClient(
            "https://example.supabase.co",
            "anon-key",
            timeout_seconds=5,
            opener=opener,
        )

        client.download_object(
            authorization="Bearer valid",
            bucket="source-documents",
            object_path="owner/project/upload/source",
            max_bytes=1024,
        )

        self.assertEqual(
            captured_urls,
            [
                "https://example.supabase.co/storage/v1/object/source-documents/owner/project/upload/source"
            ],
        )


if __name__ == "__main__":
    unittest.main()
