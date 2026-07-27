import hashlib
import json
import tempfile
import unittest
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import unquote, urlsplit

from specgraph_foundry.database import Database
from specgraph_foundry.http_api.auth import (
    AuthenticationError,
)
from specgraph_foundry.http_api.gateway import (
    AuthenticatedApi,
    new_request,
)
from specgraph_foundry.http_api.models import (
    Principal,
)
from specgraph_foundry.http_api.source_uploads import (
    SourceUploadService,
    SourceUploadSettings,
)
from specgraph_foundry.http_api.storage import (
    SupabaseStorageClient,
)
from specgraph_foundry.ingestion import (
    IngestionService,
)


class FakeAuthenticator:
    def __init__(
        self,
        principal: Principal,
    ) -> None:
        self.principal = principal

    def authenticate(
        self,
        authorization: str | None,
    ) -> Principal:
        if authorization != "Bearer valid":
            raise AuthenticationError(
                "valid bearer token required"
            )

        return self.principal


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


class FakeStorageTransport:
    def __init__(
        self,
        origin: str,
    ) -> None:
        self.origin = origin.rstrip("/")
        self.objects: dict[
            tuple[str, str],
            tuple[bytes, str]
        ] = {}
        self.invalid_signing_response = False
        self.cross_origin_sign_url = False
        self.download_error: (
            Exception | None
        ) = None

    def store_object(
        self,
        bucket: str,
        object_path: str,
        data: bytes,
        media_type: str,
    ) -> None:
        self.objects[(bucket, object_path)] = (
            data,
            media_type,
        )

    def __call__(
        self,
        request,
        timeout,
    ):
        path = urlsplit(
            request.full_url
        ).path

        if path.startswith(
            "/storage/v1/object/upload/sign/"
        ):
            suffix = path.removeprefix(
                "/storage/v1/object/upload/sign/"
            )
            bucket, _, object_path = suffix.partition(
                "/"
            )
            if self.invalid_signing_response:
                return FakeResponse(
                    b"not-json",
                    url=request.full_url,
                )
            sign_origin = (
                "https://evil.example"
                if self.cross_origin_sign_url
                else self.origin
            )
            payload = json.dumps(
                {
                    "url": (
                        f"{sign_origin}/storage/v1/object/upload/"
                        f"{bucket}/{object_path}"
                        "?token=signed"
                    )
                }
            ).encode("utf-8")
            return FakeResponse(
                payload,
                url=request.full_url,
            )

        if path.startswith(
            "/storage/v1/object/authenticated/"
        ):
            if self.download_error is not None:
                raise self.download_error
            suffix = path.removeprefix(
                "/storage/v1/object/authenticated/"
            )
            bucket, _, object_path = suffix.partition(
                "/"
            )
            key = (
                unquote(bucket),
                unquote(object_path),
            )
            if key not in self.objects:
                raise HTTPError(
                    request.full_url,
                    404,
                    "not found",
                    {},
                    None,
                )
            payload, media_type = self.objects[key]
            return FakeResponse(
                payload,
                url=request.full_url,
                headers={"content-type": media_type},
            )

        raise AssertionError(
            f"unexpected storage path: {path}"
        )


class SourceUploadsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = (
            tempfile.TemporaryDirectory()
        )
        self.database = Database(
            Path(self.temp.name)
            / "source-uploads.sqlite3"
        )
        self.database.initialize()
        self.principal = Principal(
            user_id=str(uuid.uuid4()),
            email="owner@example.com",
        )
        self.transport = FakeStorageTransport(
            "https://example.supabase.co"
        )
        self.source_uploads = (
            SourceUploadService(
                self.database,
                SupabaseStorageClient(
                    "https://example.supabase.co",
                    "anon-key",
                    timeout_seconds=5,
                    opener=self.transport,
                ),
                SourceUploadSettings(
                    bucket="source-documents",
                    upload_url_ttl_seconds=900,
                    max_source_bytes=4096,
                ),
            )
        )
        self.application = AuthenticatedApi(
            self.database,
            FakeAuthenticator(
                self.principal
            ),
            source_uploads=self.source_uploads,
            enforce_mutation_guards=True,
        )
        created = self.request(
            "POST",
            "/v1/projects",
            {
                "slug": "source-upload-project",
                "name": "Source Upload Project",
            },
        )
        self.project_id = str(
            created.body["id"]
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def request(
        self,
        method: str,
        path: str,
        payload: (
            dict[str, object] | None
        ) = None,
        *,
        idempotency_key: str | None = None,
        principal: Principal | None = None,
    ):
        headers = {
            "Authorization": "Bearer valid"
        }
        if idempotency_key is not None:
            headers["Idempotency-Key"] = (
                idempotency_key
            )
        application = self.application
        if principal is not None:
            application = AuthenticatedApi(
                self.database,
                FakeAuthenticator(principal),
                source_uploads=self.source_uploads,
                enforce_mutation_guards=True,
            )

        return application.dispatch(
            new_request(
                method,
                path,
                headers,
                payload or {},
            )
        )

    def test_create_intent_and_status_are_safe(
        self,
    ) -> None:
        body = b"# Source\n\nExact bytes.\n"
        digest = hashlib.sha256(body).hexdigest()

        created = self.request(
            "POST",
            f"/v1/projects/{self.project_id}/source-uploads",
            {
                "filename": "requirements.md",
                "media_type": "text/markdown",
                "byte_size": len(body),
                "sha256": digest,
            },
            idempotency_key="upload-intent-key-0001",
        )

        self.assertEqual(created.status, 201)
        self.assertEqual(
            created.body["status"],
            "PENDING",
        )
        self.assertTrue(
            created.body["object_path"].startswith(
                f"{self.principal.user_id}/{self.project_id}/"
            )
        )
        self.assertIn(
            "signed_upload_url",
            created.body,
        )
        self.assertEqual(
            created.headers["idempotency-replayed"],
            "false",
        )

        replay = self.request(
            "POST",
            f"/v1/projects/{self.project_id}/source-uploads",
            {
                "filename": "requirements.md",
                "media_type": "text/markdown",
                "byte_size": len(body),
                "sha256": digest,
            },
            idempotency_key="upload-intent-key-0001",
        )

        self.assertEqual(replay.status, 201)
        self.assertEqual(
            replay.headers["idempotency-replayed"],
            "true",
        )
        self.assertEqual(
            replay.body["id"],
            created.body["id"],
        )

        status = self.request(
            "GET",
            f"/v1/source-uploads/{created.body['id']}",
        )
        self.assertEqual(status.status, 200)
        self.assertNotIn(
            "signed_upload_url",
            status.body,
        )
        self.assertEqual(
            status.body["status"],
            "PENDING",
        )

    def test_finalize_creates_one_document_and_replays(
        self,
    ) -> None:
        body = b"line 1\r\nline 2\r\n"
        digest = hashlib.sha256(body).hexdigest()
        created = self.request(
            "POST",
            f"/v1/projects/{self.project_id}/source-uploads",
            {
                "filename": "line-endings.txt",
                "media_type": "text/plain",
                "byte_size": len(body),
                "sha256": digest,
            },
            idempotency_key="upload-intent-key-0002",
        )

        self.transport.store_object(
            "source-documents",
            str(created.body["object_path"]),
            body,
            "text/plain",
        )

        finalized = self.request(
            "POST",
            f"/v1/source-uploads/{created.body['id']}/finalize",
            {},
            idempotency_key="upload-finalize-key-01",
        )

        self.assertEqual(finalized.status, 201)
        self.assertEqual(
            finalized.body["status"],
            "FINALIZED",
        )
        self.assertEqual(
            finalized.headers["idempotency-replayed"],
            "false",
        )

        replay = self.request(
            "POST",
            f"/v1/source-uploads/{created.body['id']}/finalize",
            {},
            idempotency_key="upload-finalize-key-01",
        )

        self.assertEqual(replay.status, 201)
        self.assertEqual(
            replay.headers["idempotency-replayed"],
            "true",
        )
        self.assertEqual(
            replay.body["document_id"],
            finalized.body["document_id"],
        )

        reconstructed = IngestionService(
            self.database
        ).reconstruct(
            finalized.body["document_id"]
        )
        self.assertEqual(
            reconstructed,
            b"line 1\nline 2\n",
        )
        self.assertEqual(
            finalized.body["raw_authority"]["sha256"],
            digest,
        )
        self.assertEqual(
            finalized.body["raw_authority"]["byte_count"],
            len(body),
        )
        self.assertEqual(
            finalized.body["derivation"]["derived_sha256"],
            hashlib.sha256(
                b"line 1\nline 2\n"
            ).hexdigest(),
        )

        with self.database.connect() as connection:
            upload_row = connection.execute(
                """
                SELECT
                    actual_bytes,
                    actual_sha256,
                    document_id
                FROM source_uploads
                WHERE id = ?
                """,
                (created.body["id"],),
            ).fetchone()
            document_row = connection.execute(
                """
                SELECT source_upload_id
                FROM source_documents
                WHERE id = ?
                """,
                (finalized.body["document_id"],),
            ).fetchone()

        self.assertEqual(
            int(upload_row["actual_bytes"]),
            len(body),
        )
        self.assertEqual(
            str(upload_row["actual_sha256"]),
            digest,
        )
        self.assertEqual(
            str(upload_row["document_id"]),
            finalized.body["document_id"],
        )
        self.assertEqual(
            str(document_row["source_upload_id"]),
            created.body["id"],
        )

    def test_invalid_filename_and_media_type_are_rejected(
        self,
    ) -> None:
        invalid_name = self.request(
            "POST",
            f"/v1/projects/{self.project_id}/source-uploads",
            {
                "filename": "../secret.txt",
                "media_type": "text/plain",
                "byte_size": 4,
                "sha256": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            },
            idempotency_key="upload-invalid-name-1",
        )
        self.assertEqual(
            invalid_name.status,
            400,
        )
        self.assertEqual(
            invalid_name.body["error"]["code"],
            "INVALID_FILENAME",
        )

        invalid_type = self.request(
            "POST",
            f"/v1/projects/{self.project_id}/source-uploads",
            {
                "filename": "binary.pdf",
                "media_type": "application/octet-stream",
                "byte_size": 4,
                "sha256": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            },
            idempotency_key="upload-invalid-type-1",
        )
        self.assertEqual(
            invalid_type.status,
            415,
        )
        self.assertEqual(
            invalid_type.body["error"]["code"],
            "UNSUPPORTED_MEDIA_TYPE",
        )

    def test_unknown_or_outsider_upload_is_hidden(
        self,
    ) -> None:
        outsider = Principal(
            user_id=str(uuid.uuid4()),
            email="other@example.com",
        )
        body = b"hello\n"
        created = self.request(
            "POST",
            f"/v1/projects/{self.project_id}/source-uploads",
            {
                "filename": "hello.txt",
                "media_type": "text/plain",
                "byte_size": len(body),
                "sha256": hashlib.sha256(body).hexdigest(),
            },
            idempotency_key="upload-visibility-1",
        )

        hidden = self.request(
            "GET",
            f"/v1/source-uploads/{created.body['id']}",
            principal=outsider,
        )
        self.assertEqual(hidden.status, 404)
        self.assertEqual(
            hidden.body["error"]["code"],
            "NOT_FOUND",
        )

    def test_finalize_rejects_missing_hash_mismatch_and_invalid_utf8(
        self,
    ) -> None:
        body = b"hello\n"
        digest = hashlib.sha256(body).hexdigest()
        created = self.request(
            "POST",
            f"/v1/projects/{self.project_id}/source-uploads",
            {
                "filename": "missing.txt",
                "media_type": "text/plain",
                "byte_size": len(body),
                "sha256": digest,
            },
            idempotency_key="upload-missing-object",
        )

        missing = self.request(
            "POST",
            f"/v1/source-uploads/{created.body['id']}/finalize",
            {},
            idempotency_key="upload-missing-finalize",
        )
        self.assertEqual(missing.status, 409)
        self.assertEqual(
            missing.body["error"]["code"],
            "UPLOAD_STATE_CONFLICT",
        )

        mismatch = self.request(
            "POST",
            f"/v1/projects/{self.project_id}/source-uploads",
            {
                "filename": "mismatch.txt",
                "media_type": "text/plain",
                "byte_size": len(body),
                "sha256": digest,
            },
            idempotency_key="upload-hash-mismatch",
        )
        self.transport.store_object(
            "source-documents",
            str(mismatch.body["object_path"]),
            b"other\n",
            "text/plain",
        )
        mismatch_result = self.request(
            "POST",
            f"/v1/source-uploads/{mismatch.body['id']}/finalize",
            {},
            idempotency_key="upload-hash-finalize",
        )
        self.assertEqual(
            mismatch_result.status,
            409,
        )
        self.assertEqual(
            mismatch_result.body["error"]["code"],
            "UPLOAD_INTEGRITY_MISMATCH",
        )

        invalid = self.request(
            "POST",
            f"/v1/projects/{self.project_id}/source-uploads",
            {
                "filename": "invalid.txt",
                "media_type": "text/plain",
                "byte_size": 2,
                "sha256": hashlib.sha256(
                    b"\xff\xfe"
                ).hexdigest(),
            },
            idempotency_key="upload-invalid-utf8",
        )
        self.transport.store_object(
            "source-documents",
            str(invalid.body["object_path"]),
            b"\xff\xfe",
            "text/plain",
        )
        invalid_result = self.request(
            "POST",
            f"/v1/source-uploads/{invalid.body['id']}/finalize",
            {},
            idempotency_key="upload-invalid-finalize",
        )
        self.assertEqual(
            invalid_result.status,
            400,
        )
        self.assertEqual(
            invalid_result.body["error"]["code"],
            "INVALID_SOURCE_ENCODING",
        )


if __name__ == "__main__":
    unittest.main()
