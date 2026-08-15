"""Starting an execution run.

`start_run` turns a verified plan into a run with a node per plan node. It is
the only place a run comes into existence, and the only unit here that reads the
planning side -- which is why it takes a `PlanningService` and nothing else in
the execution path does.
"""

from __future__ import annotations

import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .execution_events import record_event
from .execution_queries import get_run
from .exports import ExportService
from .planning import PlanningService
from .primitives import canonical_json, new_id, utc_now

STAGES = {
    "CONTRACT",
    "IMPLEMENTATION",
    "VERIFICATION",
}


def start_run(
    database: Database,
    planning: PlanningService,
    exports: ExportService,
    plan_id: str,
    runtime_system: str,
    runtime_run_id: str,
    export_id: str | None = None,
) -> dict[str, object]:
    runtime_system = runtime_system.strip()
    runtime_run_id = runtime_run_id.strip()

    if not runtime_system:
        raise ValidationError(
            "runtime_system is required"
        )

    if not runtime_run_id:
        raise ValidationError(
            "runtime_run_id is required"
        )

    plan = planning.get_plan(
        plan_id
    )

    if plan["status"] != "VERIFIED":
        raise ValidationError(
            "execution requires a VERIFIED plan"
        )

    if export_id is not None:
        export = exports.get_export(
            export_id
        )

        if export["status"] != "VERIFIED":
            raise ValidationError(
                "execution export must be VERIFIED"
            )

        if (
            export["plan_version_id"]
            != plan_id
        ):
            raise ValidationError(
                "export does not belong to plan"
            )

    run_id = new_id("execution-run")
    timestamp = utc_now()

    try:
        with database.connect() as connection:
            connection.execute(
                """
                INSERT INTO execution_runs(
                    id,
                    project_id,
                    plan_version_id,
                    export_id,
                    runtime_system,
                    runtime_run_id,
                    status,
                    input_fingerprint,
                    created_at,
                    started_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    run_id,
                    plan["project_id"],
                    plan_id,
                    export_id,
                    runtime_system,
                    runtime_run_id,
                    "RUNNING",
                    plan[
                        "input_fingerprint"
                    ],
                    timestamp,
                    timestamp,
                ),
            )

            for binding in plan[
                "bindings"
            ]:
                stage = str(
                    binding["stage"]
                )

                if stage not in STAGES:
                    raise ValidationError(
                        f"invalid plan stage: "
                        f"{stage}"
                    )

                connection.execute(
                    """
                    INSERT INTO
                        execution_run_nodes(
                            id,
                            run_id,
                            graph_node_id,
                            atom_id,
                            stage,
                            sequence_number,
                            title,
                            status,
                            created_at,
                            updated_at
                        )
                    VALUES(?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        new_id(
                            "execution-node"
                        ),
                        run_id,
                        binding[
                            "graph_node_id"
                        ],
                        binding["atom_id"],
                        stage,
                        binding[
                            "sequence_number"
                        ],
                        binding[
                            "canonical_statement"
                        ],
                        "PENDING",
                        timestamp,
                        timestamp,
                    ),
                )

            record_event(
                connection,
                run_id,
                None,
                "RUN_STARTED",
                runtime_system,
                {
                    "plan_id": plan_id,
                    "export_id": export_id,
                    "runtime_run_id": (
                        runtime_run_id
                    ),
                },
            )

    except sqlite3.IntegrityError as error:
        raise ConflictError(
            "runtime run identifier already exists"
        ) from error

    return get_run(database, run_id)
