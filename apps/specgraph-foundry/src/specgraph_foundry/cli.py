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
from .planning import PlanningService
from .exports import ExportService
from .execution import ExecutionService
from .routing import RoutingService
from .services import GraphService, ProjectService





from . import (
    cli_document_commands,
    cli_execution_commands,
    cli_export_commands,
    cli_planning_commands,
    cli_project_commands,
    cli_research_commands,
    cli_routing_commands,
)
from .cli_output import output

#: Tried in order; the first group that owns the command handles it.
COMMAND_GROUPS = (
    cli_project_commands,
    cli_document_commands,
    cli_research_commands,
    cli_planning_commands,
    cli_export_commands,
    cli_execution_commands,
    cli_routing_commands,
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

    for group in COMMAND_GROUPS:
        group.register(commands, settings)

    return parser


def main() -> int:
    settings = Settings.from_environment()
    args = build_parser().parse_args()

    database = Database(
        settings.database_path,
        database_url=settings.database_url,
        owner_id=settings.database_owner_id,
    )
    database.initialize()

    projects = ProjectService(database)
    ingestion = IngestionService(database)
    atoms = AtomService(database)
    research = ResearchService(database)
    planning = PlanningService(database)
    exports = ExportService(database)
    execution = ExecutionService(database)
    routing = RoutingService(database)
    graphs = GraphService(database)

    for group in COMMAND_GROUPS:
        if group.handle(
            args=args,
            atoms=atoms,
            database=database,
            execution=execution,
            exports=exports,
            graphs=graphs,
            ingestion=ingestion,
            planning=planning,
            projects=projects,
            research=research,
            routing=routing,
            settings=settings,
        ):
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


# `python -m specgraph_foundry.cli <command>` imports this module rather than
# running it, so without this guard every such invocation exited 0 having done
# nothing at all. A command that silently succeeds without acting is worse than
# one that fails: the caller's `set -e` never fires and the next step runs on
# state that was never created.
if __name__ == "__main__":
    raise SystemExit(main())
