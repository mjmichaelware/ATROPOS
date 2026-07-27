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

        if request.get_method() == "GET" and path.startswith(prefix):
            object_path = path[len(prefix) :]
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

    def test_get_export_survives_a_missing_artifact_manifest_row(self) -> None:
        # Exports created through any path that never persisted an
        # artifact_manifests row (e.g. pre-dating durable artifact storage)
        # must still be viewable - list_exports already tolerates this;
        # get_export previously raised NotFoundError and surfaced as a
        # broken detail view for an otherwise real, valid export.
        created = self.request(
            "POST",
            f"/v1/plans/{self.plan_id}/exports",
            {},
            idempotency_key="durable-export-key-0005",
        )
        export_id = str(created.body["id"])
        with self.database.connect() as connection:
            connection.execute(
                "DELETE FROM artifact_manifests WHERE export_id = ?",
                (export_id,),
            )

        response = self.request("GET", f"/v1/exports/{export_id}")

        self.assertEqual(response.status, 200)
        self.assertEqual(response.body["id"], export_id)
        self.assertIsNone(response.body["artifact_manifest"])

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

    def test_cancellation_mid_export_marks_the_partial_export_invalid(self) -> None:
        # export_plan() now reports progress after every individual
        # artifact upload/verify (so long exports keep their operation
        # lease alive and show real progress) - operations.progress()
        # raises OperationCancelled if cancellation was requested while
        # that loop is still running. Regression test that this no longer
        # skips past both existing except clauses and leaves the export
        # row / manifest stuck in a non-terminal state with only some
        # objects uploaded.
        from specgraph_foundry.http_api.operations import OperationCancelled

        calls = 0

        def cancel_after_first_artifact(current: int, total: int) -> None:
            nonlocal calls
            calls += 1
            if calls == 1:
                raise OperationCancelled("operation cancellation requested")

        with self.assertRaises(OperationCancelled):
            self.durable_exports.export_plan(
                owner_id=self.principal.user_id,
                authorization="Bearer valid",
                plan_id=self.plan_id,
                on_progress=cancel_after_first_artifact,
            )

        with self.database.connect() as connection:
            manifest_state = connection.execute(
                "SELECT state FROM artifact_manifests"
            ).fetchone()["state"]
            object_states = {
                row["state"]
                for row in connection.execute(
                    "SELECT state FROM storage_objects"
                ).fetchall()
            }
        self.assertEqual(manifest_state, "INVALID")
        self.assertEqual(object_states, {"INVALID"})

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

    def test_download_legacy_export_returns_actionable_error(self) -> None:
        # Exports created before durable artifact storage have no
        # artifact_manifests row. Calling download() must raise
        # ValidationError (→ HTTP 400 with a helpful message) not
        # NotFoundError (→ HTTP 404 "resource not found"), so the UI can
        # surface "generate a new export" guidance instead of a dead end.
        from specgraph_foundry.errors import ValidationError

        fake_export_id = str(uuid.uuid4())
        with self.assertRaises(ValidationError) as ctx:
            self.durable_exports.download(
                owner_id=self.principal.user_id,
                authorization="Bearer valid",
                export_id=fake_export_id,
            )
        self.assertIn("Generate a new export", str(ctx.exception))

    def test_download_legacy_export_via_http_returns_400(self) -> None:
        # Same check exercised through the full HTTP dispatch layer, confirming
        # the gateway maps the ValidationError to a 400 (not a 404).
        fake_export_id = str(uuid.uuid4())
        response = self.request(
            "GET",
            f"/v1/exports/{fake_export_id}/download",
        )
        self.assertEqual(response.status, 400)
        self.assertIn(
            "Generate a new export",
            str(response.body),
        )


if __name__ == "__main__":
    unittest.main()
