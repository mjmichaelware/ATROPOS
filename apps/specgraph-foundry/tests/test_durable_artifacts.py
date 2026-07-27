import json
import tempfile
import unittest
import uuid
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import unquote, urlparse

from specgraph_foundry.atoms import AtomService
from specgraph_foundry.database import Database
from specgraph_foundry.http_api.artifact_storage import (
    ArtifactStorageClient,
    ArtifactStorageSettings,
)
from specgraph_foundry.http_api.durable_exports import (
    DurableExportService,
)
from specgraph_foundry.http_api.gateway import (
    AuthenticatedApi,
    new_request,
)
from specgraph_foundry.http_api.models import Principal
from specgraph_foundry.http_api.storage import SupabaseStorageClient
from specgraph_foundry.ingestion import IngestionService
from specgraph_foundry.planning import PlanningService
from specgraph_foundry.services import ProjectService


class FakeAuthenticator:
    def __init__(self, principal: Principal) -> None:
        self.principal = principal

    def authenticate(self, authorization: str | None) -> Principal:
        if authorization != "Bearer valid":
            raise AssertionError("unexpected authorization")
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

    def __exit__(self, *_args) -> bool:
        return False

    def read(self, size: int = -1) -> bytes:
        if size < 0:
            return self.payload
        return self.payload[:size]

    def geturl(self) -> str:
        return self.url


class FakeArtifactTransport:
    def __init__(self) -> None:
        self.objects: dict[str, tuple[bytes, str]] = {}
        self.tamper: dict[str, bytes] = {}

    def __call__(self, request, timeout):
        parsed = urlparse(request.full_url)
        path = unquote(parsed.path)
        prefix = "/storage/v1/object/export-artifacts/"
        auth_prefix = (
            "/storage/v1/object/authenticated/export-artifacts/"
        )
        sign_prefix = "/storage/v1/object/sign/export-artifacts/"

        if request.get_method() == "POST" and path.startswith(prefix):
            object_path = path[len(prefix) :]
            if object_path in self.objects:
                raise HTTPError(
                    request.full_url,
                    409,
                    "exists",
                    {},
                    None,
                )
            media_type = request.headers.get(
                "Content-type",
                request.headers.get("Content-Type", "application/octet-stream"),
            )
            self.objects[object_path] = (
                request.data or b"",
                media_type,
            )
            return FakeResponse(
                b"{}",
                url=request.full_url,
            )

        if request.get_method() == "GET" and path.startswith(auth_prefix):
            object_path = path[len(auth_prefix) :]
            if object_path not in self.objects:
                raise HTTPError(
                    request.full_url,
                    404,
                    "missing",
                    {},
                    None,
                )
            data, media_type = self.objects[object_path]
            return FakeResponse(
                self.tamper.get(object_path, data),
                url=request.full_url,
                headers={"content-type": media_type},
            )

        if request.get_method() == "POST" and path.startswith(sign_prefix):
            object_path = path[len(sign_prefix) :]
            if object_path not in self.objects:
                raise HTTPError(
                    request.full_url,
                    404,
                    "missing",
                    {},
                    None,
                )
            payload = json.dumps(
                {
                    "signedURL": (
                        "https://example.supabase.co/storage/v1/object/sign/"
                        f"export-artifacts/{object_path}?token=fake"
                    )
                }
            ).encode("utf-8")
            return FakeResponse(
                payload,
                url=request.full_url,
            )

        raise AssertionError(f"unexpected storage request: {path}")


class DurableArtifactTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.database = Database(
            self.root / "durable.sqlite3"
        )
        self.database.initialize()
        self.principal = Principal(
            user_id=str(uuid.uuid4()),
            email="owner@example.com",
        )
        self.transport = FakeArtifactTransport()
        storage = SupabaseStorageClient(
            "https://example.supabase.co",
            "anon-key",
            timeout_seconds=5,
            opener=self.transport,
        )
        self.durable_exports = DurableExportService(
            self.database,
            ArtifactStorageClient(
                storage,
                ArtifactStorageSettings(
                    bucket="export-artifacts",
                    max_artifact_bytes=10 * 1024 * 1024,
                    download_ttl_seconds=300,
                ),
            ),
        )
        self.application = AuthenticatedApi(
            self.database,
            FakeAuthenticator(self.principal),
            durable_exports=self.durable_exports,
            enforce_mutation_guards=True,
        )
        self.project_id, self.plan_id = self._fixture()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def _fixture(self) -> tuple[str, str]:
        project = ProjectService(self.database).create(
            "durable-artifacts",
            "Durable Artifacts",
        )
        document = IngestionService(self.database).ingest_text(
            str(project["id"]),
            "Authority",
            "The application must preserve export artifacts.\n",
            chunk_bytes=64,
        )
        AtomService(self.database).extract_document(
            str(document["id"])
        )
        planning = PlanningService(self.database)
        plan = planning.synthesize(
            str(project["id"]),
            allow_open_research=True,
        )
        planning.verify_plan(str(plan["id"]))
        return str(project["id"]), str(plan["id"])

    def request(
        self,
        method: str,
        path: str,
        payload: dict[str, object] | None = None,
        *,
        idempotency_key: str | None = None,
    ):
        headers = {"Authorization": "Bearer valid"}
        if idempotency_key is not None:
            headers["Idempotency-Key"] = idempotency_key
        return self.application.dispatch(
            new_request(
                method,
                path,
                headers,
                payload or {},
            )
        )

    def test_generation_persists_verified_manifest_and_objects(self) -> None:
        response = self.request(
            "POST",
            f"/v1/plans/{self.plan_id}/exports",
            {},
            idempotency_key="durable-export-key-0001",
        )

        self.assertEqual(response.status, 201)
        self.assertEqual(response.body["status"], "VERIFIED")
        self.assertEqual(
            response.headers["idempotency-replayed"],
            "false",
        )
        self.assertIsNone(response.body["output_path"])
        self.assertEqual(response.body["artifacts"], [])
        manifest = response.body["artifact_manifest"]
        self.assertEqual(manifest["state"], "VERIFIED")
        self.assertGreater(manifest["artifact_count"], 0)
        self.assertNotIn("signed_download_url", json.dumps(response.body))

        with self.database.connect() as connection:
            objects = connection.execute(
                """
                SELECT *
                FROM storage_objects
                WHERE state = 'VERIFIED'
                """
            ).fetchall()
            manifests = connection.execute(
                """
                SELECT *
                FROM artifact_manifests
                WHERE state = 'VERIFIED'
                """
            ).fetchall()
        self.assertEqual(len(manifests), 1)
        self.assertEqual(len(objects), manifest["artifact_count"])
        self.assertEqual(
            len(self.transport.objects),
            manifest["artifact_count"],
        )
        for path in self.transport.objects:
            self.assertTrue(
                path.startswith(f"{self.principal.user_id}/{self.project_id}/")
            )

    def test_replay_does_not_duplicate_artifacts(self) -> None:
        key = "durable-export-key-0002"
        first = self.request(
            "POST",
            f"/v1/plans/{self.plan_id}/exports",
            {},
            idempotency_key=key,
        )
        second = self.request(
            "POST",
            f"/v1/plans/{self.plan_id}/exports",
            {},
            idempotency_key=key,
        )
        self.assertEqual(second.status, 201)
        self.assertEqual(
            second.headers["idempotency-replayed"],
            "true",
        )
        self.assertEqual(first.body["id"], second.body["id"])
        with self.database.connect() as connection:
            manifest_count = connection.execute(
                "SELECT COUNT(*) AS value FROM artifact_manifests"
            ).fetchone()["value"]
        self.assertEqual(manifest_count, 1)

    def test_tampered_download_blocks_verification(self) -> None:
        original = self.request(
            "POST",
            f"/v1/plans/{self.plan_id}/exports",
            {},
            idempotency_key="durable-export-key-0003",
        )
        export_id = str(original.body["id"])
        object_path = next(iter(self.transport.objects))
        self.transport.tamper[object_path] = b"tampered"

        response = self.request(
            "POST",
            f"/v1/exports/{export_id}/verify",
            {},
            idempotency_key="durable-verify-key-0003",
        )

        self.assertEqual(response.status, 409)
        self.assertEqual(
            response.body["error"]["code"],
            "ARTIFACT_INTEGRITY_FAILED",
        )

    def test_verified_download_returns_short_lived_urls(self) -> None:
        created = self.request(
            "POST",
            f"/v1/plans/{self.plan_id}/exports",
            {},
            idempotency_key="durable-export-key-0004",
        )

        response = self.request(
            "GET",
            f"/v1/exports/{created.body['id']}/download",
        )

        self.assertEqual(response.status, 200)
        self.assertEqual(response.body["expires_in"], 300)
        self.assertGreater(len(response.body["artifacts"]), 0)
        self.assertIn(
            "signed_download_url",
            response.body["artifacts"][0],
        )
        with self.database.connect() as connection:
            raw = "\n".join(
                str(row["manifest_json"])
                for row in connection.execute(
                    "SELECT manifest_json FROM artifact_manifests"
                ).fetchall()
            )
        self.assertNotIn("signed_download_url", raw)

    def test_execution_requires_verified_durable_manifest(self) -> None:
        created = self.request(
            "POST",
            f"/v1/plans/{self.plan_id}/exports",
            {},
            idempotency_key="durable-export-key-0005",
        )
        export_id = str(created.body["id"])
        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE artifact_manifests
                SET state = 'INVALID'
                WHERE export_id = ?
                """,
                (export_id,),
            )

        blocked = self.request(
            "POST",
            f"/v1/plans/{self.plan_id}/execution-runs",
            {
                "runtime_system": "ATROPOS",
                "runtime_run_id": "runtime-blocked",
                "export_id": export_id,
            },
            idempotency_key="durable-run-key-0005",
        )
        self.assertEqual(blocked.status, 409)

        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE artifact_manifests
                SET state = 'VERIFIED'
                WHERE export_id = ?
                """,
                (export_id,),
            )
        allowed = self.request(
            "POST",
            f"/v1/plans/{self.plan_id}/execution-runs",
            {
                "runtime_system": "ATROPOS",
                "runtime_run_id": "runtime-allowed",
                "export_id": export_id,
            },
            idempotency_key="durable-run-key-0006",
        )
        self.assertEqual(allowed.status, 201)


if __name__ == "__main__":
    unittest.main()
