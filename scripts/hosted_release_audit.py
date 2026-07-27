import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import uuid
from pathlib import Path

from specgraph_foundry.atoms import (
    AtomService,
)
from specgraph_foundry.database import (
    Database,
)
from specgraph_foundry.execution import (
    ExecutionService,
)
from specgraph_foundry.exports import (
    ExportService,
)
from specgraph_foundry.ingestion import (
    IngestionService,
)
from specgraph_foundry.planning import (
    PlanningService,
)
from specgraph_foundry.research import (
    ResearchService,
)
from specgraph_foundry.routing import (
    RoutingService,
)
from specgraph_foundry.services import (
    ProjectService,
)


ROOT = Path(__file__).resolve().parents[1]


def require(
    condition: bool,
    message: str,
) -> None:
    if not condition:
        raise RuntimeError(message)


def canonical_json(
    value: object,
) -> str:
    return json.dumps(
        value,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    )


def sha256_bytes(
    value: bytes,
) -> str:
    return hashlib.sha256(
        value
    ).hexdigest()


def sha256_file(
    path: Path,
) -> str:
    return sha256_bytes(
        path.read_bytes()
    )


def run_command(
    command: list[str],
) -> dict[str, object]:
    environment = os.environ.copy()
    environment.pop(
        "SPECGRAPH_DATABASE_URL",
        None,
    )
    environment.pop(
        "SPECGRAPH_OWNER_ID",
        None,
    )

    result = subprocess.run(
        command,
        cwd=ROOT,
        env=environment,
        text=True,
        capture_output=True,
        check=False,
    )

    combined = (
        result.stdout
        + result.stderr
    ).encode("utf-8")

    require(
        result.returncode == 0,
        (
            "Command failed: "
            + " ".join(command)
            + "\n"
            + result.stdout
            + result.stderr
        ),
    )

    return {
        "command": " ".join(command),
        "exit_code": result.returncode,
        "stdout_sha256": (
            sha256_bytes(combined)
        ),
        "output": combined.decode(
            "utf-8",
            errors="replace",
        ),
    }


def verify_rls(
    database_url: str,
    project_id: str,
    owner_id: str,
) -> dict[str, object]:
    import psycopg
    from psycopg.rows import dict_row

    project_uuid = uuid.UUID(
        project_id
    )
    owner_uuid = uuid.UUID(owner_id)
    outsider_uuid = uuid.uuid4()

    def visible_count(
        subject: uuid.UUID,
    ) -> int:
        claims = canonical_json(
            {
                "sub": str(subject),
                "role": "authenticated",
            }
        )

        with psycopg.connect(
            database_url,
            row_factory=dict_row,
            prepare_threshold=None,
        ) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    "SET LOCAL ROLE authenticated"
                )
                cursor.execute(
                    """
                    SELECT set_config(
                        'request.jwt.claim.sub',
                        %s,
                        true
                    )
                    """,
                    (str(subject),),
                )
                cursor.execute(
                    """
                    SELECT set_config(
                        'request.jwt.claim.role',
                        'authenticated',
                        true
                    )
                    """
                )
                cursor.execute(
                    """
                    SELECT set_config(
                        'request.jwt.claims',
                        %s,
                        true
                    )
                    """,
                    (claims,),
                )
                cursor.execute(
                    """
                    SELECT count(*) AS count
                    FROM public.projects
                    WHERE id = %s
                    """,
                    (project_uuid,),
                )

                return int(
                    cursor.fetchone()[
                        "count"
                    ]
                )

    owner_visible = (
        visible_count(owner_uuid) == 1
    )

    outsider_hidden = (
        visible_count(outsider_uuid) == 0
    )

    write_isolation = False

    try:
        claims = canonical_json(
            {
                "sub": str(
                    outsider_uuid
                ),
                "role": "authenticated",
            }
        )

        with psycopg.connect(
            database_url,
            row_factory=dict_row,
            prepare_threshold=None,
        ) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    "SET LOCAL ROLE authenticated"
                )
                cursor.execute(
                    """
                    SELECT set_config(
                        'request.jwt.claim.sub',
                        %s,
                        true
                    )
                    """,
                    (
                        str(
                            outsider_uuid
                        ),
                    ),
                )
                cursor.execute(
                    """
                    SELECT set_config(
                        'request.jwt.claim.role',
                        'authenticated',
                        true
                    )
                    """
                )
                cursor.execute(
                    """
                    SELECT set_config(
                        'request.jwt.claims',
                        %s,
                        true
                    )
                    """,
                    (claims,),
                )
                cursor.execute(
                    """
                    INSERT INTO public.projects(
                        id,
                        owner_id,
                        slug,
                        name,
                        description
                    )
                    VALUES(%s,%s,%s,%s,%s)
                    """,
                    (
                        uuid.uuid4(),
                        owner_uuid,
                        (
                            "forbidden-"
                            + uuid.uuid4().hex
                        ),
                        "Forbidden project",
                        "",
                    ),
                )
    except psycopg.Error:
        write_isolation = True

    anon_blocked = False

    try:
        with psycopg.connect(
            database_url,
            row_factory=dict_row,
            prepare_threshold=None,
        ) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    "SET LOCAL ROLE anon"
                )
                cursor.execute(
                    """
                    SELECT count(*)
                    FROM public.projects
                    """
                )
    except psycopg.Error:
        anon_blocked = True

    require(
        owner_visible,
        "RLS owner could not read project",
    )
    require(
        outsider_hidden,
        "RLS exposed project to outsider",
    )
    require(
        write_isolation,
        "RLS allowed outsider project insert",
    )
    require(
        anon_blocked,
        "Anonymous role retained table access",
    )

    return {
        "owner_visible": (
            owner_visible
        ),
        "outsider_hidden": (
            outsider_hidden
        ),
        "outsider_write_blocked": (
            write_isolation
        ),
        "anonymous_blocked": (
            anon_blocked
        ),
    }


def resolve_research(
    research: ResearchService,
    project_id: str,
) -> int:
    completed = 0

    while True:
        worker_id = (
            "hosted-researcher-"
            + str(completed)
        )

        task = research.claim_task(
            project_id,
            worker_id,
            lease_seconds=300,
        )

        if task is None:
            break

        evidence = research.add_evidence(
            task_id=str(task["id"]),
            worker_id=worker_id,
            source_uri=(
                "urn:specgraph:"
                "hosted-release-audit:"
                + str(task["id"])
            ),
            source_title=(
                "Hosted release authority"
            ),
            excerpt=(
                "This completeness dimension "
                "is applicable and must remain "
                "traceable to the source atom."
            ),
            publisher=(
                "SpecGraph Foundry audit"
            ),
            evidence_type=(
                "TEST_RESULT"
            ),
            reliability=1.0,
        )

        research.complete_task(
            task_id=str(task["id"]),
            worker_id=worker_id,
            conclusion=(
                "Hosted verification confirms "
                "this dimension is applicable "
                "and evidence-backed."
            ),
            applicability="APPLICABLE",
            confidence=1.0,
            evidence_ids=[
                str(evidence["id"])
            ],
        )

        completed += 1

    return completed


def implementation_evidence(
    atom_id: str,
    compile_result: dict[str, object],
) -> dict[str, object]:
    changed_files = [
        {
            "path": (
                "src/specgraph_foundry/"
                "database.py"
            ),
            "sha256": sha256_file(
                ROOT
                / "src/specgraph_foundry/"
                "database.py"
            ),
            "responsibility": (
                "Provides compatible SQLite "
                "and PostgreSQL connection "
                "semantics."
            ),
        },
        {
            "path": (
                "scripts/"
                "hosted_release_audit.py"
            ),
            "sha256": sha256_file(
                ROOT
                / "scripts/"
                "hosted_release_audit.py"
            ),
            "responsibility": (
                "Executes the hosted release "
                "verification workflow."
            ),
        },
    ]

    implementation_manifest = (
        canonical_json(changed_files)
        .encode("utf-8")
    )

    return {
        "source_atom_ids": [
            atom_id
        ],
        "changed_files": (
            changed_files
        ),
        "commands": [
            {
                "command": (
                    compile_result[
                        "command"
                    ]
                ),
                "exit_code": (
                    compile_result[
                        "exit_code"
                    ]
                ),
                "stdout_sha256": (
                    compile_result[
                        "stdout_sha256"
                    ]
                ),
            }
        ],
        "diff_sha256": (
            sha256_bytes(
                implementation_manifest
            )
        ),
        "call_sites": [
            (
                "src/specgraph_foundry/"
                "cli.py:main"
            ),
            (
                "src/specgraph_foundry/"
                "api.py:Api"
            ),
        ],
        "reachability": [
            (
                "CLI or API -> service layer "
                "-> Database.connect -> "
                "hosted PostgreSQL"
            ),
            (
                "ATROPOS handoff -> "
                "execution run -> receipt "
                "validation"
            ),
        ],
        "rollback": {
            "strategy": (
                "Revert the hosted release "
                "commit and retain the SQLite "
                "offline backend."
            ),
            "recovery_command": (
                "git revert RELEASE_COMMIT_SHA"
            ),
        },
    }


def verification_evidence(
    atom_id: str,
    implementation_receipt_id: str,
    test_result: dict[str, object],
) -> tuple[
    dict[str, object],
    int,
]:
    output = str(
        test_result["output"]
    )

    match = re.search(
        r"Ran\s+(\d+)\s+tests?",
        output,
    )

    test_count = (
        int(match.group(1))
        if match
        else 1
    )

    return (
        {
            "source_atom_ids": [
                atom_id
            ],
            "commands": [
                {
                    "command": (
                        test_result[
                            "command"
                        ]
                    ),
                    "exit_code": (
                        test_result[
                            "exit_code"
                        ]
                    ),
                    "stdout_sha256": (
                        test_result[
                            "stdout_sha256"
                        ]
                    ),
                }
            ],
            "tests": [
                {
                    "name": (
                        "SpecGraph complete "
                        "unit-test suite"
                    ),
                    "status": "PASSED",
                    "assertions": (
                        test_count
                    ),
                },
                {
                    "name": (
                        "Hosted PostgreSQL "
                        "pipeline verification"
                    ),
                    "status": "PASSED",
                    "assertions": 1,
                },
            ],
            "independent_verification": (
                True
            ),
            "verified_receipt_ids": [
                implementation_receipt_id
            ],
        },
        test_count,
    )


def run_audit(
    database_url: str,
    owner_id: str,
) -> dict[str, object]:
    database = Database(
        ROOT
        / ".specgraph/"
        "hosted-release-audit.sqlite3",
        database_url=database_url,
        owner_id=owner_id,
    )

    database.initialize()

    projects = ProjectService(database)
    ingestion = IngestionService(
        database
    )
    atoms = AtomService(database)
    research = ResearchService(
        database
    )
    planning = PlanningService(
        database
    )
    exports = ExportService(
        database
    )
    routing = RoutingService(
        database
    )
    execution = ExecutionService(
        database
    )

    owner = None

    with database.connect() as connection:
        owner = connection.execute(
            """
            SELECT id
            FROM auth.users
            WHERE id = ?
            """,
            (owner_id,),
        ).fetchone()

    require(
        owner is not None,
        (
            "SPECGRAPH_OWNER_ID does not "
            "exist in auth.users"
        ),
    )

    suffix = uuid.uuid4().hex[:12]
    project = None

    try:
        project = projects.create(
            f"hosted-audit-{suffix}",
            "Hosted Release Audit",
            (
                "Temporary project created by "
                "the SpecGraph Foundry backend "
                "release audit."
            ),
        )

        project_id = str(
            project["id"]
        )

        rls = verify_rls(
            database_url,
            project_id,
            owner_id,
        )

        document = ingestion.ingest_text(
            project_id=project_id,
            title=(
                "Hosted release authority"
            ),
            content=(
                "The hosted runtime must "
                "preserve source authority "
                "through independently verified "
                "execution receipts.\n"
            ),
            chunk_bytes=48,
        )

        document_verification = (
            ingestion.verify_document(
                str(document["id"])
            )
        )

        require(
            document_verification["valid"],
            "Hosted source verification failed",
        )

        extraction = (
            atoms.extract_document(
                str(document["id"])
            )
        )

        require(
            extraction["atom_count"] == 1,
            (
                "Audit authority must produce "
                "exactly one atom"
            ),
        )

        atom = extraction["atoms"][0]
        atom_id = str(atom["id"])

        research_count = (
            resolve_research(
                research,
                project_id,
            )
        )

        gap_matrix = research.gap_matrix(
            project_id
        )

        require(
            gap_matrix["summary"]["open_dimensions"]
            == 0,
            (
                "Research dimensions remain "
                "open"
            ),
        )

        plan = planning.synthesize(
            project_id,
            allow_open_research=False,
        )

        plan_verification = (
            planning.verify_plan(
                str(plan["id"])
            )
        )

        require(
            (plan_verification["status"] == "VERIFIED"),
            "Hosted plan verification failed",
        )

        binding = exports.bind_integration(
            project_id=project_id,
            system_name="ATROPOS",
            binding_type=(
                "AUTONOMOUS_RUNTIME"
            ),
            config={
                "repository": (
                    "mjmichaelware/ATROPOS"
                ),
                "transport": (
                    "VERIFIED_HANDOFF_BUNDLE"
                ),
                "receipt_protocol": (
                    "SPECGRAPH_V1"
                ),
            },
        )

        routing.set_policy(
            project_id=project_id,
            allow_offline_degraded=True,
            paid_emergency_enabled=False,
            max_paid_decisions_per_unlock=1,
        )

        provider = (
            routing.configure_provider(
                project_id=project_id,
                name=(
                    "ATROPOS_LOCAL_TOOLCHAIN"
                ),
                provider_class=(
                    "LOCAL_TOOLCHAIN"
                ),
                cost_class="LOCAL",
                territories=[
                    "CODE_PATCH",
                    "BUILD",
                    "TEST",
                ],
                priority=0,
                metadata={
                    "runtime": "ATROPOS",
                    "platform": (
                        "ANDROID_TERMUX"
                    ),
                },
            )
        )

        routing.record_health(
            str(provider["id"]),
            "READY",
            latency_ms=0.0,
        )

        route_decision = routing.route(
            project_id,
            "CODE_PATCH",
            offline_capable=True,
        )

        require(
            route_decision[
                "decision_type"
            ]
            == "LOCAL_TOOLCHAIN",
            (
                "Canonical routing did not "
                "select local ATROPOS"
            ),
        )

        renderer = (
            routing.configure_renderer(
                project_id=project_id,
                name=(
                    "ATROPOS_HANDOFF_JSON"
                ),
                renderer_type="JSON",
                territories=[
                    "BLUEPRINT",
                    "EXECUTION_HANDOFF",
                ],
                priority=0,
                metadata={
                    "schema": (
                        "SPECGRAPH_V1"
                    )
                },
            )
        )

        selected_renderer = (
            routing.select_renderer(
                project_id,
                "EXECUTION_HANDOFF",
            )
        )

        require(
            selected_renderer is not None
            and selected_renderer["id"]
            == renderer["id"],
            "Renderer selection failed",
        )

        with tempfile.TemporaryDirectory(
            prefix="specgraph-hosted-audit-"
        ) as output_directory:
            exported = exports.export_plan(
                str(plan["id"]),
                Path(output_directory),
            )

            require(
                exported["status"]
                == "VERIFIED",
                "Hosted export was not verified",
            )

            export_verification = (
                exports.verify_export(
                    str(exported["id"])
                )
            )

            require(
                export_verification["valid"],
                "Hosted export verification failed",
            )

            compile_result = run_command(
                [
                    sys.executable,
                    "-m",
                    "compileall",
                    "-q",
                    "src",
                ]
            )

            run = execution.start_run(
                plan_id=str(plan["id"]),
                runtime_system="ATROPOS",
                runtime_run_id=(
                    "hosted-audit-"
                    + uuid.uuid4().hex
                ),
                export_id=str(
                    exported["id"]
                ),
            )

            run_id = str(run["id"])

            contract_claim = (
                execution.claim_node(
                    run_id,
                    (
                        "atropos-contract-"
                        "compiler"
                    ),
                    lease_seconds=300,
                )
            )

            require(
                contract_claim is not None,
                "Contract node was not ready",
            )

            require(
                contract_claim[
                    "node"
                ]["stage"]
                == "CONTRACT",
                (
                    "First execution stage "
                    "was not CONTRACT"
                ),
            )

            contract_receipt = (
                execution.submit_receipt(
                    run_node_id=str(
                        contract_claim[
                            "node"
                        ]["id"]
                    ),
                    worker_id=(
                        "atropos-contract-"
                        "compiler"
                    ),
                    actor_system="ATROPOS",
                    outcome="SUCCESS",
                    summary=(
                        "Compiled source-grounded "
                        "acceptance criteria for "
                        "the hosted runtime."
                    ),
                    evidence={
                        "source_atom_ids": [
                            atom_id
                        ],
                        "acceptance_criteria": [
                            (
                                "The hosted runtime "
                                "preserves source "
                                "authority."
                            ),
                            (
                                "Completion requires "
                                "independent receipt "
                                "verification."
                            ),
                        ],
                    },
                )
            )

            require(
                contract_receipt[
                    "validation_status"
                ]
                == "ACCEPTED",
                "Contract receipt was rejected",
            )

            rejected_claim = (
                execution.claim_node(
                    run_id,
                    "atropos-builder",
                    lease_seconds=300,
                )
            )

            require(
                rejected_claim is not None,
                (
                    "Implementation node "
                    "was not ready"
                ),
            )

            rejected_receipt = (
                execution.submit_receipt(
                    run_node_id=str(
                        rejected_claim[
                            "node"
                        ]["id"]
                    ),
                    worker_id=(
                        "atropos-builder"
                    ),
                    actor_system="ATROPOS",
                    outcome="SUCCESS",
                    summary=(
                        "Attempted implementation "
                        "without sufficient runtime "
                        "evidence."
                    ),
                    evidence={
                        "source_atom_ids": [
                            atom_id
                        ]
                    },
                )
            )

            rejected_codes = {
                finding["gate_code"]
                for finding
                in rejected_receipt[
                    "findings"
                ]
            }

            require(
                rejected_receipt[
                    "validation_status"
                ]
                == "REJECTED",
                (
                    "Empty implementation "
                    "was not rejected"
                ),
            )

            require(
                "NO_EMPTY_IMPLEMENTATION"
                in rejected_codes,
                (
                    "Required empty-"
                    "implementation gate "
                    "did not fire"
                ),
            )

            implementation_claim = (
                execution.claim_node(
                    run_id,
                    "atropos-builder",
                    lease_seconds=300,
                )
            )

            require(
                implementation_claim
                is not None,
                (
                    "Rejected node could not "
                    "be reclaimed"
                ),
            )

            implementation_data = (
                implementation_evidence(
                    atom_id,
                    compile_result,
                )
            )

            implementation_receipt = (
                execution.submit_receipt(
                    run_node_id=str(
                        implementation_claim[
                            "node"
                        ]["id"]
                    ),
                    worker_id=(
                        "atropos-builder"
                    ),
                    actor_system="ATROPOS",
                    outcome="SUCCESS",
                    summary=(
                        "Implemented the hosted "
                        "PostgreSQL execution path "
                        "with connected CLI, API, "
                        "and receipt call sites."
                    ),
                    evidence=(
                        implementation_data
                    ),
                )
            )

            require(
                implementation_receipt[
                    "validation_status"
                ]
                == "ACCEPTED",
                (
                    "Valid implementation "
                    "receipt was rejected"
                ),
            )

            test_result = run_command(
                [
                    sys.executable,
                    "-m",
                    "unittest",
                    "discover",
                    "-s",
                    "tests",
                    "-v",
                ]
            )

            verification_data, test_count = (
                verification_evidence(
                    atom_id,
                    str(
                        implementation_receipt[
                            "id"
                        ]
                    ),
                    test_result,
                )
            )

            self_verification_claim = (
                execution.claim_node(
                    run_id,
                    "atropos-builder",
                    lease_seconds=300,
                )
            )

            require(
                self_verification_claim
                is not None,
                (
                    "Verification node "
                    "was not ready"
                ),
            )

            self_verification = (
                execution.submit_receipt(
                    run_node_id=str(
                        self_verification_claim[
                            "node"
                        ]["id"]
                    ),
                    worker_id=(
                        "atropos-builder"
                    ),
                    actor_system="ATROPOS",
                    outcome="SUCCESS",
                    summary=(
                        "Attempted to verify the "
                        "same implementation using "
                        "the implementation actor."
                    ),
                    evidence=(
                        verification_data
                    ),
                )
            )

            self_verification_codes = {
                finding["gate_code"]
                for finding
                in self_verification[
                    "findings"
                ]
            }

            require(
                self_verification[
                    "validation_status"
                ]
                == "REJECTED",
                (
                    "Self-verification was "
                    "not rejected"
                ),
            )

            require(
                "NO_SELF_VERIFICATION"
                in self_verification_codes,
                (
                    "Self-verification gate "
                    "did not fire"
                ),
            )

            verification_claim = (
                execution.claim_node(
                    run_id,
                    (
                        "independent-specgraph-"
                        "verifier"
                    ),
                    lease_seconds=300,
                )
            )

            require(
                verification_claim is not None,
                (
                    "Verification node could "
                    "not be reclaimed"
                ),
            )

            verification_receipt = (
                execution.submit_receipt(
                    run_node_id=str(
                        verification_claim[
                            "node"
                        ]["id"]
                    ),
                    worker_id=(
                        "independent-specgraph-"
                        "verifier"
                    ),
                    actor_system=(
                        "SPECGRAPH_FOUNDRY"
                    ),
                    outcome="SUCCESS",
                    summary=(
                        "Independently verified "
                        "the hosted runtime, unit "
                        "suite, execution evidence, "
                        "and source traceability."
                    ),
                    evidence=(
                        verification_data
                    ),
                )
            )

            require(
                verification_receipt[
                    "validation_status"
                ]
                == "ACCEPTED",
                (
                    "Independent verification "
                    "receipt was rejected"
                ),
            )

            first_verification = (
                execution.verify_run(
                    run_id
                )
            )

            require(
                first_verification["valid"],
                (
                    "Completed hosted run "
                    "did not verify"
                ),
            )

            with database.connect() as connection:
                connection.execute(
                    """
                    UPDATE execution_receipts
                    SET evidence_json = ?
                    WHERE id = ?
                    """,
                    (
                        canonical_json(
                            {
                                "tampered": True
                            }
                        ),
                        str(
                            implementation_receipt[
                                "id"
                            ]
                        ),
                    ),
                )

            tampered_verification = (
                execution.verify_run(
                    run_id
                )
            )

            tamper_codes = {
                finding["gate_code"]
                for finding
                in tampered_verification[
                    "findings"
                ]
            }

            require(
                not tampered_verification[
                    "valid"
                ],
                (
                    "Tampered receipt remained "
                    "valid"
                ),
            )

            require(
                "EVIDENCE_HASH_MISMATCH"
                in tamper_codes,
                (
                    "Tamper hash gate did "
                    "not fire"
                ),
            )

            with database.connect() as connection:
                connection.execute(
                    """
                    UPDATE execution_receipts
                    SET evidence_json = ?
                    WHERE id = ?
                    """,
                    (
                        canonical_json(
                            implementation_data
                        ),
                        str(
                            implementation_receipt[
                                "id"
                            ]
                        ),
                    ),
                )

            final_verification = (
                execution.verify_run(
                    run_id
                )
            )

            require(
                final_verification["valid"],
                (
                    "Restored execution run "
                    "did not reverify"
                ),
            )

            final_run = execution.get_run(
                run_id
            )

            report = {
                "release": (
                    "specgraph-foundry-"
                    "backend-v1"
                ),
                "valid": True,
                "backend": (
                    database.health()
                ),
                "security": rls,
                "source": {
                    "document_id": str(
                        document["id"]
                    ),
                    "document_valid": (
                        document_verification[
                            "valid"
                        ]
                    ),
                    "atom_id": atom_id,
                    "atom_count": (
                        extraction[
                            "atom_count"
                        ]
                    ),
                },
                "research": {
                    "tasks_completed": (
                        research_count
                    ),
                    "open_dimensions": (
                        gap_matrix["summary"]["open_dimensions"]
                    ),
                },
                "planning": {
                    "plan_id": str(
                        plan["id"]
                    ),
                    "status": plan[
                        "status"
                    ],
                    "valid": (
                        (plan_verification["status"] == "VERIFIED")
                    ),
                    "node_count": plan[
                        "node_count"
                    ],
                },
                "integration": {
                    "binding_id": str(
                        binding["id"]
                    ),
                    "system": (
                        binding[
                            "system_name"
                        ]
                    ),
                    "route": (
                        route_decision[
                            "decision_type"
                        ]
                    ),
                    "renderer": (
                        selected_renderer[
                            "name"
                        ]
                    ),
                },
                "export": {
                    "export_id": str(
                        exported["id"]
                    ),
                    "status": (
                        exported["status"]
                    ),
                    "valid": (
                        export_verification[
                            "valid"
                        ]
                    ),
                    "artifact_count": (
                        exported[
                            "artifact_count"
                        ]
                    ),
                },
                "execution": {
                    "run_id": run_id,
                    "status": (
                        final_run["status"]
                    ),
                    "valid": (
                        final_verification[
                            "valid"
                        ]
                    ),
                    "unit_test_count": (
                        test_count
                    ),
                    "empty_implementation_rejected": (
                        "NO_EMPTY_IMPLEMENTATION"
                        in rejected_codes
                    ),
                    "self_verification_rejected": (
                        "NO_SELF_VERIFICATION"
                        in self_verification_codes
                    ),
                    "tamper_detected": (
                        "EVIDENCE_HASH_MISMATCH"
                        in tamper_codes
                    ),
                },
            }

            return report

    finally:
        if project is not None:
            try:
                with database.connect() as connection:
                    connection.execute(
                        """
                        DELETE FROM projects
                        WHERE id = ?
                        """,
                        (
                            str(
                                project["id"]
                            ),
                        ),
                    )
            except Exception as cleanup_error:
                print(
                    (
                        "WARNING: temporary "
                        "project cleanup failed: "
                        f"{cleanup_error}"
                    ),
                    file=sys.stderr,
                )


def main() -> int:
    database_url = os.environ.get(
        "SPECGRAPH_DATABASE_URL",
        "",
    ).strip()

    owner_id = os.environ.get(
        "SPECGRAPH_OWNER_ID",
        "",
    ).strip()

    if not database_url:
        raise SystemExit(
            "SPECGRAPH_DATABASE_URL is required"
        )

    if not owner_id:
        raise SystemExit(
            "SPECGRAPH_OWNER_ID is required"
        )

    report = run_audit(
        database_url,
        owner_id,
    )

    print(
        json.dumps(
            report,
            indent=2,
            sort_keys=True,
        )
    )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
