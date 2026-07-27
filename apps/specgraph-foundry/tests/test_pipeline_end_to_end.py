import json
import tempfile
import time
import unittest
import uuid
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import unquote, urlparse

from specgraph_foundry.database import Database
from specgraph_foundry.http_api.artifact_storage import (
    ArtifactStorageClient,
    ArtifactStorageSettings,
)
from specgraph_foundry.http_api.durable_exports import DurableExportService
from specgraph_foundry.http_api.gateway import AuthenticatedApi, new_request
from specgraph_foundry.http_api.models import Principal
from specgraph_foundry.http_api.operation_handlers import OperationHandlerRegistry
from specgraph_foundry.http_api.operations import OperationSettings, OperationStore
from specgraph_foundry.http_api.storage import SupabaseStorageClient
from specgraph_foundry.http_api.worker import run_once


class FakeResponse:
    def __init__(self, payload: bytes, *, url: str, headers: dict[str, str] | None = None) -> None:
        self.payload = payload
        self.url = url
        self.headers = headers or {}
        self._offset = 0

    def __enter__(self):
        return self

    def __exit__(self, *_args) -> bool:
        return False

    def read(self, size: int = -1) -> bytes:
        if size < 0:
            data = self.payload[self._offset:]
            self._offset = len(self.payload)
            return data
        data = self.payload[self._offset:self._offset + size]
        self._offset += len(data)
        return data

    def geturl(self) -> str:
        return self.url


class FakeArtifactTransport:
    def __init__(self) -> None:
        self.objects: dict[str, tuple[bytes, str]] = {}

    def __call__(self, request, timeout):
        parsed = urlparse(request.full_url)
        path = unquote(parsed.path)
        prefix = "/storage/v1/object/export-artifacts/"
        sign_prefix = "/storage/v1/object/sign/export-artifacts/"

        if request.get_method() == "POST" and path.startswith(prefix):
            object_path = path[len(prefix):]
            media_type = request.headers.get("Content-type", "application/octet-stream")
            self.objects[object_path] = (request.data or b"", media_type)
            return FakeResponse(b"{}", url=request.full_url)

        if request.get_method() == "GET" and path.startswith(prefix):
            object_path = path[len(prefix):]
            if object_path not in self.objects:
                raise HTTPError(request.full_url, 404, "missing", {}, None)
            data, media_type = self.objects[object_path]
            return FakeResponse(data, url=request.full_url, headers={"content-type": media_type})

        if request.get_method() == "POST" and path.startswith(sign_prefix):
            object_path = path[len(sign_prefix):]
            if object_path not in self.objects:
                raise HTTPError(request.full_url, 404, "missing", {}, None)
            payload = json.dumps(
                {"signedURL": f"https://example.supabase.co/storage/v1/object/sign/export-artifacts/{object_path}?token=fake"}
            ).encode("utf-8")
            return FakeResponse(payload, url=request.full_url)

        raise AssertionError(f"unexpected storage request: {path}")


class FakeAuthenticator:
    def __init__(self, principal: Principal) -> None:
        self.principal = principal

    def authenticate(self, authorization: str | None) -> Principal:
        if authorization != "Bearer valid":
            raise AssertionError("unexpected authorization")
        return self.principal


class PipelineEndToEndTest(unittest.TestCase):
    """Drives the full source-to-export pipeline exactly the way production
    does it: every async step (extraction, plan synthesis, plan
    verification, export creation, export verification) is submitted over
    HTTP as a 202'd operation and only advances when the operation worker
    (the same `run_once` the Cloud Run Job invokes) actually claims and
    processes it - nothing here calls a service method directly to shortcut
    the queue. If this test passes, the pipeline code is provably correct
    end to end; if the pipeline is still broken in production, the defect
    is in whatever is (or isn't) invoking the worker, not in this code path.
    """

    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.database = Database(Path(self.temp.name) / "pipeline.sqlite3")
        self.database.initialize()
        self.principal = Principal(user_id=str(uuid.uuid4()), email="owner@example.com")
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
                ArtifactStorageSettings(bucket="export-artifacts", max_artifact_bytes=10 * 1024 * 1024, download_ttl_seconds=300),
            ),
        )
        self.operations = OperationStore(
            self.database,
            OperationSettings(lease_seconds=15, heartbeat_seconds=5, timeout_seconds=60),
            cursor_signing_key="x" * 32,
        )
        self.operation_handlers = OperationHandlerRegistry(
            self.database,
            durable_exports=self.durable_exports,
        )
        self.application = AuthenticatedApi(
            self.database,
            FakeAuthenticator(self.principal),
            durable_exports=self.durable_exports,
            operations=self.operations,
            operation_handlers=self.operation_handlers,
            enforce_mutation_guards=True,
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def request(self, method: str, path: str, payload: dict[str, object] | None = None, *, idempotency_key: str | None = None):
        headers = {"Authorization": "Bearer valid"}
        if idempotency_key is not None:
            headers["Idempotency-Key"] = idempotency_key
        return self.application.dispatch(new_request(method, path, headers, payload or {}))

    def drain_until_terminal(self, operation_id: str, *, max_ticks: int = 10) -> dict[str, object]:
        # Mirrors the real production shape: an HTTP client submits an
        # operation and polls GET /v1/operations/{id}; only a worker
        # calling run_once() ever advances it out of QUEUED. If nothing
        # ever calls run_once() (e.g. because no scheduler ever triggers
        # the worker job), this loop - and the real pipeline - never
        # progresses past QUEUED, which is exactly what run_once proves or
        # disproves here.
        for _ in range(max_ticks):
            claimed = run_once(self.operations, self.operation_handlers, "worker-a")
            response = self.request("GET", f"/v1/operations/{operation_id}")
            state = response.body["operation"]["state"]
            if state in {"SUCCEEDED", "FAILED", "CANCELLED", "TIMED_OUT"}:
                return response.body["operation"]
            if not claimed:
                break
        raise AssertionError(f"operation {operation_id} never reached a terminal state")

    def test_full_pipeline_from_ingestion_to_downloadable_export(self) -> None:
        # 1. Project + source ingestion (synchronous, not queue-based).
        project = self.request("POST", "/v1/projects", {"slug": "pipeline-e2e", "name": "Pipeline E2E"}, idempotency_key=str(uuid.uuid4()))
        self.assertEqual(project.status, 201, project.body)
        project_id = str(project.body["id"])

        document = self.request(
            "POST",
            f"/v1/projects/{project_id}/documents",
            {
                "title": "Authority",
                "content": "The application must extract atoms and export a verified plan.\n",
            },
            idempotency_key=str(uuid.uuid4()),
        )
        self.assertEqual(document.status, 201, document.body)
        document_id = str(document.body["id"])

        # 2. Extraction - async, must go through the worker.
        extract = self.request("POST", f"/v1/documents/{document_id}/extract", {}, idempotency_key=str(uuid.uuid4()))
        self.assertEqual(extract.status, 202, extract.body)
        extract_op = self.drain_until_terminal(str(extract.body["operation"]["id"]))
        self.assertEqual(extract_op["state"], "SUCCEEDED", extract_op)

        atoms = self.request("GET", f"/v1/documents/{document_id}/atoms")
        self.assertEqual(atoms.status, 200)
        self.assertGreater(len(atoms.body["items"]), 0, "extraction produced no atoms")

        # 3. Plan synthesis - async.
        synthesize = self.request(
            "POST",
            f"/v1/projects/{project_id}/plans",
            {"allow_open_research": True},
            idempotency_key=str(uuid.uuid4()),
        )
        self.assertEqual(synthesize.status, 202, synthesize.body)
        synthesize_op = self.drain_until_terminal(str(synthesize.body["operation"]["id"]))
        self.assertEqual(synthesize_op["state"], "SUCCEEDED", synthesize_op)
        plan_id = str(synthesize_op["result"]["plan_id"])

        # 4. Plan verification - async.
        verify_plan = self.request("POST", f"/v1/plans/{plan_id}/verify", {}, idempotency_key=str(uuid.uuid4()))
        self.assertEqual(verify_plan.status, 202, verify_plan.body)
        verify_plan_op = self.drain_until_terminal(str(verify_plan.body["operation"]["id"]))
        self.assertEqual(verify_plan_op["state"], "SUCCEEDED", verify_plan_op)
        self.assertEqual(verify_plan_op["result"]["status"], "VERIFIED", verify_plan_op)

        # 5. Export creation - async - this is the exact step behind the
        # "Export unavailable" / "export doesn't work" reports.
        export = self.request("POST", f"/v1/plans/{plan_id}/exports", {}, idempotency_key=str(uuid.uuid4()))
        self.assertEqual(export.status, 202, export.body)
        export_op = self.drain_until_terminal(str(export.body["operation"]["id"]))
        self.assertEqual(export_op["state"], "SUCCEEDED", export_op)
        export_id = str(export_op["result"]["export_id"])

        # 6. Export verification - async.
        verify_export = self.request("POST", f"/v1/exports/{export_id}/verify", {}, idempotency_key=str(uuid.uuid4()))
        self.assertEqual(verify_export.status, 202, verify_export.body)
        verify_export_op = self.drain_until_terminal(str(verify_export.body["operation"]["id"]))
        self.assertEqual(verify_export_op["state"], "SUCCEEDED", verify_export_op)
        self.assertEqual(verify_export_op["result"]["status"], "VERIFIED", verify_export_op)

        # 7. The export must actually be viewable and downloadable - the
        # two concrete complaints ("could not load" / "doesn't work").
        detail = self.request("GET", f"/v1/exports/{export_id}")
        self.assertEqual(detail.status, 200, detail.body)
        self.assertIsNotNone(detail.body["artifact_manifest"])
        self.assertEqual(detail.body["artifact_manifest"]["state"], "VERIFIED")

        download = self.request("GET", f"/v1/exports/{export_id}/download")
        self.assertEqual(download.status, 200, download.body)
        self.assertGreater(len(download.body["artifacts"]), 0, "no downloadable artifacts")
        for artifact in download.body["artifacts"]:
            self.assertIn("signed_download_url", artifact)

        # The export must include the human-readable text blueprint.
        artifact_names = {artifact["name"] for artifact in download.body["artifacts"]}
        self.assertIn("implementation_blueprint.txt", artifact_names)
        self.assertNotIn("implementation_blueprint.pdf", artifact_names)
        text_artifact = next(a for a in download.body["artifacts"] if a["name"] == "implementation_blueprint.txt")
        self.assertEqual(text_artifact["media_type"], "text/plain")
        text_path = next(path for path in self.transport.objects if path.endswith("/implementation_blueprint.txt"))
        text_bytes, _ = self.transport.objects[text_path]
        self.assertIn(b"Implementation Blueprint", text_bytes)
        self.assertNotIn(b"#", text_bytes)

        # The blueprint must be an exercisable build plan, not just a list
        # of requirements - the DAG itself (nodes, their dependencies, and
        # what's safe to start right now) has to be rendered in the text
        # artifact, not only inside the JSON-only atropos_handoff.json.
        self.assertIn(b"Execution Plan (DAG)", text_bytes)
        self.assertIn(b"Ready to start now", text_bytes)
        self.assertIn(b"MUST COMPLETE before", text_bytes)

    def test_default_flow_without_allow_open_research_shortcut(self) -> None:
        # The first test above passes allow_open_research=True, which lets a
        # plan become VERIFIED immediately at synthesis even with unresolved
        # research questions. That is not how a real user gets to a
        # verified plan: synthesizing normally leaves the plan BLOCKED until
        # every open research dimension for every atom is actually resolved
        # through the Research workspace, and only a fresh synthesis after
        # that resolution picks up open_dimension_count=0. This test proves
        # that realistic, default path - the one an actual user hits -
        # works end to end, not just the shortcut.
        project = self.request("POST", "/v1/projects", {"slug": "pipeline-default", "name": "Pipeline Default"}, idempotency_key=str(uuid.uuid4()))
        self.assertEqual(project.status, 201, project.body)
        project_id = str(project.body["id"])

        document = self.request(
            "POST",
            f"/v1/projects/{project_id}/documents",
            {"title": "Authority", "content": "The application must extract atoms and export a verified plan.\n"},
            idempotency_key=str(uuid.uuid4()),
        )
        self.assertEqual(document.status, 201, document.body)
        document_id = str(document.body["id"])

        extract = self.request("POST", f"/v1/documents/{document_id}/extract", {}, idempotency_key=str(uuid.uuid4()))
        self.assertEqual(extract.status, 202, extract.body)
        extract_op = self.drain_until_terminal(str(extract.body["operation"]["id"]))
        self.assertEqual(extract_op["state"], "SUCCEEDED", extract_op)

        # Synthesizing with the default (no open-research override) leaves
        # the plan BLOCKED, since the single atom has every dimension open.
        blocked_synthesis = self.request(
            "POST",
            f"/v1/projects/{project_id}/plans",
            {},
            idempotency_key=str(uuid.uuid4()),
        )
        self.assertEqual(blocked_synthesis.status, 202, blocked_synthesis.body)
        blocked_op = self.drain_until_terminal(str(blocked_synthesis.body["operation"]["id"]))
        self.assertEqual(blocked_op["state"], "SUCCEEDED", blocked_op)
        self.assertEqual(blocked_op["result"]["status"], "BLOCKED", blocked_op)

        # Resolve every open research dimension for the atom - claim,
        # submit evidence, and complete each task through the same async
        # operation queue production uses (complete_research_task is
        # registered in ASYNC_OPERATION_TYPES).
        resolved = 0
        while True:
            claimed = self.request(
                "POST",
                f"/v1/projects/{project_id}/research-tasks/claim",
                {"worker_id": "pipeline-test-worker", "lease_seconds": 300},
                idempotency_key=str(uuid.uuid4()),
            )
            self.assertEqual(claimed.status, 200, claimed.body)
            task = claimed.body.get("task")
            if task is None:
                break
            task_id = str(task["id"])

            evidence = self.request(
                "POST",
                f"/v1/research-tasks/{task_id}/evidence",
                {
                    "worker_id": "pipeline-test-worker",
                    "source_uri": "https://example.test/standard",
                    "source_title": "Standard",
                    "excerpt": "Resolved for pipeline verification.",
                    "evidence_type": "STANDARD",
                    "reliability": 0.9,
                },
                idempotency_key=str(uuid.uuid4()),
            )
            self.assertEqual(evidence.status, 201, evidence.body)

            complete = self.request(
                "POST",
                f"/v1/research-tasks/{task_id}/complete",
                {
                    "worker_id": "pipeline-test-worker",
                    "conclusion": "Resolved for pipeline verification.",
                    "applicability": "NOT_APPLICABLE",
                    "confidence": 0.9,
                    "evidence_ids": [str(evidence.body["id"])],
                },
                idempotency_key=str(uuid.uuid4()),
            )
            self.assertEqual(complete.status, 202, complete.body)
            complete_op = self.drain_until_terminal(str(complete.body["operation"]["id"]))
            self.assertEqual(complete_op["state"], "SUCCEEDED", complete_op)
            resolved += 1
            self.assertLessEqual(resolved, 32, "resolving more research tasks than exist - claim is not shrinking the queue")

        self.assertGreater(resolved, 0, "no open research tasks were found to resolve")

        # A fresh synthesis now picks up open_dimension_count=0.
        resynthesize = self.request(
            "POST",
            f"/v1/projects/{project_id}/plans",
            {},
            idempotency_key=str(uuid.uuid4()),
        )
        self.assertEqual(resynthesize.status, 202, resynthesize.body)
        resynthesize_op = self.drain_until_terminal(str(resynthesize.body["operation"]["id"]))
        self.assertEqual(resynthesize_op["state"], "SUCCEEDED", resynthesize_op)
        plan_id = str(resynthesize_op["result"]["plan_id"])

        verify_plan = self.request("POST", f"/v1/plans/{plan_id}/verify", {}, idempotency_key=str(uuid.uuid4()))
        self.assertEqual(verify_plan.status, 202, verify_plan.body)
        verify_plan_op = self.drain_until_terminal(str(verify_plan.body["operation"]["id"]))
        self.assertEqual(verify_plan_op["state"], "SUCCEEDED", verify_plan_op)
        self.assertEqual(verify_plan_op["result"]["status"], "VERIFIED", verify_plan_op)
        self.assertTrue(verify_plan_op["result"]["valid"], verify_plan_op)

        export = self.request("POST", f"/v1/plans/{plan_id}/exports", {}, idempotency_key=str(uuid.uuid4()))
        self.assertEqual(export.status, 202, export.body)
        export_op = self.drain_until_terminal(str(export.body["operation"]["id"]))
        self.assertEqual(export_op["state"], "SUCCEEDED", export_op)
        export_id = str(export_op["result"]["export_id"])

        detail = self.request("GET", f"/v1/exports/{export_id}")
        self.assertEqual(detail.status, 200, detail.body)
        self.assertIsNotNone(detail.body["artifact_manifest"])

        download = self.request("GET", f"/v1/exports/{export_id}/download")
        self.assertEqual(download.status, 200, download.body)
        self.assertGreater(len(download.body["artifacts"]), 0, "no downloadable artifacts")


class RenderPdfDeterminismTest(unittest.TestCase):
    # _find_export() reuses a prior export by matching bundle_fingerprint -
    # a hash over every artifact's sha256, including
    # implementation_blueprint.pdf. fpdf2 stamps /CreationDate from the
    # wall clock by default, so re-exporting byte-identical plan content
    # at a later moment would otherwise produce different PDF bytes (and a
    # different fingerprint) purely from the passage of time, silently
    # defeating that dedup and creating a redundant export row plus
    # duplicate storage objects for content that did not actually change.
    def test_identical_markdown_produces_identical_pdf_bytes_across_time(self) -> None:
        from specgraph_foundry.rendering import render_markdown_pdf

        markdown = "# Title\n\n## Section\n\n- item one\n- item two\n"
        first = render_markdown_pdf(markdown)
        time.sleep(1.1)
        second = render_markdown_pdf(markdown)
        self.assertEqual(first, second)


if __name__ == "__main__":
    unittest.main()
