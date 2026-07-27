from pathlib import Path
from textwrap import dedent

ROOT = Path.cwd()

if ROOT.name != "specgraph-foundry" or not (ROOT / ".git").is_dir():
    raise SystemExit(f"Wrong repository: {ROOT}")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        dedent(content).lstrip(),
        encoding="utf-8",
    )
    print(f"WROTE {path}")


write(
    "src/specgraph_foundry/api.py",
    r'''
    import json
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
    from urllib.parse import urlparse

    from .atoms import AtomService
    from .database import Database
    from .errors import ConflictError, NotFoundError, ValidationError
    from .ingestion import IngestionService
    from .research import ResearchService
    from .services import ProjectService


    class Api:
        def __init__(self, database: Database) -> None:
            self.database = database
            self.projects = ProjectService(database)
            self.ingestion = IngestionService(database)
            self.atoms = AtomService(database)
            self.research = ResearchService(database)

        def dispatch(
            self,
            method: str,
            raw_path: str,
            payload: dict[str, object],
        ) -> tuple[int, dict[str, object]]:
            parts = [
                part
                for part in urlparse(raw_path).path.split("/")
                if part
            ]

            try:
                if method == "GET" and parts == ["health"]:
                    return 200, {
                        "status": "ok",
                        "service": "specgraph-foundry",
                        "database": self.database.health(),
                    }

                if parts == ["v1", "projects"]:
                    if method == "GET":
                        return 200, {
                            "items": self.projects.list()
                        }

                    if method == "POST":
                        return 201, self.projects.create(
                            str(payload.get("slug", "")),
                            str(payload.get("name", "")),
                            str(payload.get("description", "")),
                        )

                if (
                    len(parts) == 3
                    and parts[:2] == ["v1", "projects"]
                    and method == "GET"
                ):
                    return 200, self.projects.get(parts[2])

                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "projects"]
                    and parts[3] == "documents"
                ):
                    project_id = parts[2]

                    if method == "GET":
                        return 200, {
                            "items": self.ingestion.list_documents(
                                project_id
                            )
                        }

                    if method == "POST":
                        return 201, self.ingestion.ingest_text(
                            project_id=project_id,
                            title=str(payload.get("title", "")),
                            content=str(payload.get("content", "")),
                            media_type=str(
                                payload.get(
                                    "media_type",
                                    "text/plain",
                                )
                            ),
                            chunk_bytes=int(
                                payload.get(
                                    "chunk_bytes",
                                    32768,
                                )
                            ),
                        )

                if (
                    len(parts) == 3
                    and parts[:2] == ["v1", "documents"]
                    and method == "GET"
                ):
                    return 200, self.ingestion.get_document(
                        parts[2]
                    )

                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "documents"]
                    and parts[3] == "verify"
                    and method == "GET"
                ):
                    return 200, self.ingestion.verify_document(
                        parts[2]
                    )

                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "documents"]
                    and parts[3] == "extract"
                    and method == "POST"
                ):
                    return 200, self.atoms.extract_document(
                        parts[2]
                    )

                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "documents"]
                    and parts[3] == "atoms"
                    and method == "GET"
                ):
                    return 200, {
                        "items": self.atoms.list_atoms(
                            parts[2]
                        )
                    }

                if (
                    len(parts) == 3
                    and parts[:2] == ["v1", "atoms"]
                    and method == "GET"
                ):
                    return 200, self.atoms.get_atom(parts[2])

                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "projects"]
                    and parts[3] == "research-tasks"
                    and method == "GET"
                ):
                    return 200, {
                        "items": self.atoms.list_research_tasks(
                            parts[2]
                        )
                    }

                if (
                    len(parts) == 5
                    and parts[:2] == ["v1", "projects"]
                    and parts[3] == "research-tasks"
                    and parts[4] == "claim"
                    and method == "POST"
                ):
                    return 200, {
                        "task": self.research.claim_task(
                            project_id=parts[2],
                            worker_id=str(
                                payload.get("worker_id", "")
                            ),
                            lease_seconds=int(
                                payload.get(
                                    "lease_seconds",
                                    900,
                                )
                            ),
                        )
                    }

                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "projects"]
                    and parts[3] == "gap-matrix"
                    and method == "GET"
                ):
                    return 200, self.research.gap_matrix(
                        parts[2]
                    )

                if (
                    len(parts) == 3
                    and parts[:2] == ["v1", "research-tasks"]
                    and method == "GET"
                ):
                    return 200, self.research.get_task(parts[2])

                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "research-tasks"]
                    and parts[3] == "heartbeat"
                    and method == "POST"
                ):
                    return 200, self.research.heartbeat(
                        task_id=parts[2],
                        worker_id=str(
                            payload.get("worker_id", "")
                        ),
                        lease_seconds=int(
                            payload.get(
                                "lease_seconds",
                                900,
                            )
                        ),
                    )

                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "research-tasks"]
                    and parts[3] == "evidence"
                    and method == "POST"
                ):
                    return 201, self.research.add_evidence(
                        task_id=parts[2],
                        worker_id=str(
                            payload.get("worker_id", "")
                        ),
                        source_uri=str(
                            payload.get("source_uri", "")
                        ),
                        source_title=str(
                            payload.get("source_title", "")
                        ),
                        excerpt=str(
                            payload.get("excerpt", "")
                        ),
                        publisher=str(
                            payload.get("publisher", "")
                        ),
                        evidence_type=str(
                            payload.get(
                                "evidence_type",
                                "OTHER",
                            )
                        ),
                        reliability=float(
                            payload.get(
                                "reliability",
                                0.5,
                            )
                        ),
                    )

                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "research-tasks"]
                    and parts[3] == "complete"
                    and method == "POST"
                ):
                    evidence_ids = payload.get(
                        "evidence_ids",
                        [],
                    )

                    if not isinstance(evidence_ids, list):
                        raise ValidationError(
                            "evidence_ids must be a list"
                        )

                    return 200, self.research.complete_task(
                        task_id=parts[2],
                        worker_id=str(
                            payload.get("worker_id", "")
                        ),
                        conclusion=str(
                            payload.get("conclusion", "")
                        ),
                        applicability=str(
                            payload.get(
                                "applicability",
                                "",
                            )
                        ),
                        confidence=float(
                            payload.get("confidence", 0.0)
                        ),
                        evidence_ids=[
                            str(item)
                            for item in evidence_ids
                        ],
                    )

                if (
                    len(parts) == 4
                    and parts[:2] == ["v1", "research-tasks"]
                    and parts[3] == "fail"
                    and method == "POST"
                ):
                    return 200, self.research.fail_task(
                        task_id=parts[2],
                        worker_id=str(
                            payload.get("worker_id", "")
                        ),
                        error_message=str(
                            payload.get("error_message", "")
                        ),
                        retryable=bool(
                            payload.get("retryable", True)
                        ),
                    )

                return 404, {
                    "error": "ROUTE_NOT_FOUND",
                    "message": (
                        f"no route for {method} {raw_path}"
                    ),
                }

            except ValidationError as error:
                return 400, {
                    "error": "VALIDATION_ERROR",
                    "message": str(error),
                }
            except NotFoundError as error:
                return 404, {
                    "error": "NOT_FOUND",
                    "message": str(error),
                }
            except ConflictError as error:
                return 409, {
                    "error": "CONFLICT",
                    "message": str(error),
                }
            except (TypeError, ValueError) as error:
                return 400, {
                    "error": "INVALID_VALUE",
                    "message": str(error),
                }

        def serve(self, host: str, port: int) -> None:
            api = self

            class Handler(BaseHTTPRequestHandler):
                def do_GET(self) -> None:
                    self._handle()

                def do_POST(self) -> None:
                    self._handle()

                def _handle(self) -> None:
                    length = int(
                        self.headers.get(
                            "content-length",
                            "0",
                        )
                    )

                    payload: dict[str, object] = {}

                    if length:
                        try:
                            parsed = json.loads(
                                self.rfile.read(
                                    length
                                ).decode("utf-8")
                            )
                        except (
                            UnicodeDecodeError,
                            json.JSONDecodeError,
                        ):
                            self._send(
                                400,
                                {
                                    "error": "INVALID_JSON",
                                    "message": (
                                        "body must be valid JSON"
                                    ),
                                },
                            )
                            return

                        if not isinstance(parsed, dict):
                            self._send(
                                400,
                                {
                                    "error": "INVALID_JSON",
                                    "message": (
                                        "body must be a JSON object"
                                    ),
                                },
                            )
                            return

                        payload = parsed

                    status, response = api.dispatch(
                        self.command,
                        self.path,
                        payload,
                    )

                    self._send(status, response)

                def _send(
                    self,
                    status: int,
                    payload: dict[str, object],
                ) -> None:
                    encoded = json.dumps(
                        payload,
                        indent=2,
                        sort_keys=True,
                    ).encode("utf-8")

                    self.send_response(status)
                    self.send_header(
                        "content-type",
                        "application/json; charset=utf-8",
                    )
                    self.send_header(
                        "content-length",
                        str(len(encoded)),
                    )
                    self.end_headers()
                    self.wfile.write(encoded)

            server = ThreadingHTTPServer(
                (host, port),
                Handler,
            )

            print(
                "SpecGraph Foundry listening on "
                f"http://{host}:{port}"
            )

            try:
                server.serve_forever()
            except KeyboardInterrupt:
                print("\nStopping SpecGraph Foundry.")
            finally:
                server.server_close()
    ''',
)

write(
    "src/specgraph_foundry/cli.py",
    r'''
    import argparse
    import json
    import uuid
    from pathlib import Path

    from .api import Api
    from .atoms import AtomService
    from .config import Settings
    from .database import Database
    from .doctor import inspect
    from .ingestion import IngestionService
    from .research import ResearchService
    from .services import GraphService, ProjectService


    def output(value: object) -> None:
        print(
            json.dumps(
                value,
                indent=2,
                sort_keys=True,
            )
        )


    def build_parser() -> argparse.ArgumentParser:
        settings = Settings.from_environment()

        parser = argparse.ArgumentParser(
            prog="specgraph"
        )

        commands = parser.add_subparsers(
            dest="command",
            required=True,
        )

        for name in (
            "init",
            "doctor",
            "demo",
            "list-projects",
        ):
            commands.add_parser(name)

        create_project = commands.add_parser(
            "create-project"
        )
        create_project.add_argument("slug")
        create_project.add_argument("name")
        create_project.add_argument(
            "--description",
            default="",
        )

        ingest_file = commands.add_parser(
            "ingest-file"
        )
        ingest_file.add_argument("project_id")
        ingest_file.add_argument("path", type=Path)
        ingest_file.add_argument("--title")
        ingest_file.add_argument(
            "--chunk-bytes",
            type=int,
            default=32768,
        )

        document = commands.add_parser("document")
        document.add_argument("document_id")
        document.add_argument(
            "--include-chunks",
            action="store_true",
        )

        verify = commands.add_parser(
            "verify-document"
        )
        verify.add_argument("document_id")

        extract = commands.add_parser(
            "extract-document"
        )
        extract.add_argument("document_id")

        list_atoms = commands.add_parser(
            "list-atoms"
        )
        list_atoms.add_argument("document_id")

        atom = commands.add_parser("atom")
        atom.add_argument("atom_id")

        research_tasks = commands.add_parser(
            "research-tasks"
        )
        research_tasks.add_argument("project_id")
        research_tasks.add_argument("--status")

        claim = commands.add_parser(
            "claim-research"
        )
        claim.add_argument("project_id")
        claim.add_argument("worker_id")
        claim.add_argument(
            "--lease-seconds",
            type=int,
            default=900,
        )

        heartbeat = commands.add_parser(
            "research-heartbeat"
        )
        heartbeat.add_argument("task_id")
        heartbeat.add_argument("worker_id")
        heartbeat.add_argument(
            "--lease-seconds",
            type=int,
            default=900,
        )

        evidence = commands.add_parser(
            "add-evidence"
        )
        evidence.add_argument("task_id")
        evidence.add_argument("worker_id")
        evidence.add_argument("source_uri")
        evidence.add_argument("source_title")
        evidence.add_argument("excerpt")
        evidence.add_argument(
            "--publisher",
            default="",
        )
        evidence.add_argument(
            "--evidence-type",
            default="OTHER",
        )
        evidence.add_argument(
            "--reliability",
            type=float,
            default=0.5,
        )

        complete = commands.add_parser(
            "complete-research"
        )
        complete.add_argument("task_id")
        complete.add_argument("worker_id")
        complete.add_argument("conclusion")
        complete.add_argument("applicability")
        complete.add_argument(
            "confidence",
            type=float,
        )
        complete.add_argument(
            "evidence_ids",
            nargs="+",
        )

        fail = commands.add_parser(
            "fail-research"
        )
        fail.add_argument("task_id")
        fail.add_argument("worker_id")
        fail.add_argument("error_message")
        fail.add_argument(
            "--terminal",
            action="store_true",
        )

        task = commands.add_parser(
            "research-task"
        )
        task.add_argument("task_id")

        matrix = commands.add_parser(
            "gap-matrix"
        )
        matrix.add_argument("project_id")

        server = commands.add_parser("serve")
        server.add_argument(
            "--host",
            default=settings.host,
        )
        server.add_argument(
            "--port",
            type=int,
            default=settings.port,
        )

        return parser


    def main() -> int:
        settings = Settings.from_environment()
        args = build_parser().parse_args()

        database = Database(settings.database_path)
        database.initialize()

        projects = ProjectService(database)
        ingestion = IngestionService(database)
        atoms = AtomService(database)
        research = ResearchService(database)
        graphs = GraphService(database)

        if args.command == "init":
            output(database.health())
            return 0

        if args.command == "doctor":
            output(inspect())
            return 0

        if args.command == "serve":
            Api(database).serve(
                args.host,
                args.port,
            )
            return 0

        if args.command == "list-projects":
            output({"items": projects.list()})
            return 0

        if args.command == "create-project":
            output(
                projects.create(
                    args.slug,
                    args.name,
                    args.description,
                )
            )
            return 0

        if args.command == "ingest-file":
            output(
                ingestion.ingest_file(
                    project_id=args.project_id,
                    path=args.path,
                    title=args.title,
                    chunk_bytes=args.chunk_bytes,
                )
            )
            return 0

        if args.command == "document":
            output(
                ingestion.get_document(
                    args.document_id,
                    include_chunk_content=(
                        args.include_chunks
                    ),
                )
            )
            return 0

        if args.command == "verify-document":
            output(
                ingestion.verify_document(
                    args.document_id
                )
            )
            return 0

        if args.command == "extract-document":
            output(
                atoms.extract_document(
                    args.document_id
                )
            )
            return 0

        if args.command == "list-atoms":
            output(
                {
                    "items": atoms.list_atoms(
                        args.document_id
                    )
                }
            )
            return 0

        if args.command == "atom":
            output(atoms.get_atom(args.atom_id))
            return 0

        if args.command == "research-tasks":
            output(
                {
                    "items": atoms.list_research_tasks(
                        args.project_id,
                        args.status,
                    )
                }
            )
            return 0

        if args.command == "claim-research":
            output(
                {
                    "task": research.claim_task(
                        args.project_id,
                        args.worker_id,
                        args.lease_seconds,
                    )
                }
            )
            return 0

        if args.command == "research-heartbeat":
            output(
                research.heartbeat(
                    args.task_id,
                    args.worker_id,
                    args.lease_seconds,
                )
            )
            return 0

        if args.command == "add-evidence":
            output(
                research.add_evidence(
                    task_id=args.task_id,
                    worker_id=args.worker_id,
                    source_uri=args.source_uri,
                    source_title=args.source_title,
                    excerpt=args.excerpt,
                    publisher=args.publisher,
                    evidence_type=args.evidence_type,
                    reliability=args.reliability,
                )
            )
            return 0

        if args.command == "complete-research":
            output(
                research.complete_task(
                    task_id=args.task_id,
                    worker_id=args.worker_id,
                    conclusion=args.conclusion,
                    applicability=args.applicability,
                    confidence=args.confidence,
                    evidence_ids=args.evidence_ids,
                )
            )
            return 0

        if args.command == "fail-research":
            output(
                research.fail_task(
                    task_id=args.task_id,
                    worker_id=args.worker_id,
                    error_message=args.error_message,
                    retryable=not args.terminal,
                )
            )
            return 0

        if args.command == "research-task":
            output(
                research.get_task(
                    args.task_id
                )
            )
            return 0

        if args.command == "gap-matrix":
            output(
                research.gap_matrix(
                    args.project_id
                )
            )
            return 0

        suffix = uuid.uuid4().hex[:8]

        project = projects.create(
            f"demo-{suffix}",
            "SpecGraph Demonstration",
        )

        document = ingestion.ingest_text(
            project_id=str(project["id"]),
            title="Demo authority",
            content=(
                "# Contract\n"
                "Contracts must exist before implementation.\n\n"
                "## Verification\n"
                "Implementation must pass independent verification.\n"
            ),
            chunk_bytes=48,
        )

        extraction = atoms.extract_document(
            str(document["id"])
        )

        graph = graphs.create(
            str(project["id"]),
            "Demo Execution DAG",
            "EXECUTION",
            True,
        )

        contract = graphs.add_node(
            str(graph["id"]),
            "contract",
            "BATCH",
            "Define contract",
        )

        implementation = graphs.add_node(
            str(graph["id"]),
            "implementation",
            "BATCH",
            "Implement service",
        )

        verification = graphs.add_node(
            str(graph["id"]),
            "verification",
            "GATE",
            "Verify service",
        )

        graphs.add_edge(
            str(graph["id"]),
            str(contract["id"]),
            str(implementation["id"]),
            "MUST_PRECEDE",
        )

        graphs.add_edge(
            str(graph["id"]),
            str(implementation["id"]),
            str(verification["id"]),
            "MUST_PRECEDE",
        )

        output(
            {
                "project": project,
                "document": document,
                "extraction": extraction,
                "document_verification": (
                    ingestion.verify_document(
                        str(document["id"])
                    )
                ),
                "gap_matrix": research.gap_matrix(
                    str(project["id"])
                ),
                "graph": graphs.get(
                    str(graph["id"])
                ),
                "ready_nodes": graphs.ready_nodes(
                    str(graph["id"])
                ),
            }
        )

        return 0
    ''',
)

write(
    "tests/test_research_api.py",
    r'''
    import tempfile
    import unittest
    from pathlib import Path

    from specgraph_foundry.api import Api
    from specgraph_foundry.database import Database


    class ResearchApiTest(unittest.TestCase):
        def setUp(self) -> None:
            self.temp = tempfile.TemporaryDirectory()
            self.database = Database(
                Path(self.temp.name) / "test.sqlite3"
            )
            self.database.initialize()
            self.api = Api(self.database)

        def tearDown(self) -> None:
            self.temp.cleanup()

        def test_full_research_api_flow(self) -> None:
            status, project = self.api.dispatch(
                "POST",
                "/v1/projects",
                {
                    "slug": "api-test",
                    "name": "API Test",
                },
            )
            self.assertEqual(status, 201)

            project_id = str(project["id"])

            status, document = self.api.dispatch(
                "POST",
                f"/v1/projects/{project_id}/documents",
                {
                    "title": "Source",
                    "content": (
                        "The API must retain provenance.\n"
                    ),
                    "chunk_bytes": 32,
                },
            )
            self.assertEqual(status, 201)

            document_id = str(document["id"])

            status, extraction = self.api.dispatch(
                "POST",
                f"/v1/documents/{document_id}/extract",
                {},
            )
            self.assertEqual(status, 200)
            self.assertEqual(
                extraction["atom_count"],
                1,
            )

            status, claimed = self.api.dispatch(
                "POST",
                (
                    f"/v1/projects/{project_id}"
                    "/research-tasks/claim"
                ),
                {
                    "worker_id": "api-worker",
                    "lease_seconds": 300,
                },
            )
            self.assertEqual(status, 200)

            task = claimed["task"]
            task_id = str(task["id"])

            status, evidence = self.api.dispatch(
                "POST",
                (
                    f"/v1/research-tasks/{task_id}"
                    "/evidence"
                ),
                {
                    "worker_id": "api-worker",
                    "source_uri": (
                        "https://example.test/standard"
                    ),
                    "source_title": "Standard",
                    "excerpt": (
                        "Provenance must be retained."
                    ),
                    "evidence_type": "STANDARD",
                    "reliability": 0.95,
                },
            )
            self.assertEqual(status, 201)

            status, completed = self.api.dispatch(
                "POST",
                (
                    f"/v1/research-tasks/{task_id}"
                    "/complete"
                ),
                {
                    "worker_id": "api-worker",
                    "conclusion": (
                        "Durable provenance is required."
                    ),
                    "applicability": "APPLICABLE",
                    "confidence": 0.94,
                    "evidence_ids": [
                        str(evidence["id"])
                    ],
                },
            )
            self.assertEqual(status, 200)
            self.assertEqual(
                completed["status"],
                "COMPLETE",
            )

            status, matrix = self.api.dispatch(
                "GET",
                f"/v1/projects/{project_id}/gap-matrix",
                {},
            )
            self.assertEqual(status, 200)
            self.assertEqual(
                matrix["summary"][
                    "resolved_dimensions"
                ],
                1,
            )


    if __name__ == "__main__":
        unittest.main()
    ''',
)

readme = ROOT / "README.md"
content = readme.read_text(encoding="utf-8")

section = dedent(
    r'''

    ## Research evidence engine

    - durable research-task leases
    - worker ownership enforcement
    - lease expiration and reclamation
    - evidence fingerprints
    - evidence reliability scores
    - evidence-required conclusions
    - justified applicability decisions
    - task event history
    - retryable failures
    - project completeness matrices

    ```bash
    python -m specgraph_foundry claim-research PROJECT_ID WORKER_ID
    python -m specgraph_foundry research-task TASK_ID
    python -m specgraph_foundry gap-matrix PROJECT_ID
    ```
    '''
)

if "## Research evidence engine" not in content:
    readme.write_text(
        content.rstrip()
        + "\n"
        + section.lstrip(),
        encoding="utf-8",
    )
    print("UPDATED README.md")

print("RESEARCH INTEGRATION REPAIRED")
