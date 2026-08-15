"""Documents and atoms.

`main` dispatched 49 commands in a 716-line function. The branches never
interacted, so reading one meant scrolling past the other 48. One module per
domain, each answering only for its own commands.

Returns True when it handled the command, so `main` tries each group in turn
and falls through to its usage error.
"""

from __future__ import annotations

from pathlib import Path
from .cli_output import output
import json


def handle(
    args=None,
    atoms=None,
    database=None,
    execution=None,
    exports=None,
    graphs=None,
    ingestion=None,
    planning=None,
    projects=None,
    research=None,
    routing=None,
    settings=None,
) -> bool:
    """Runs the command if this group owns it. Returns whether it did."""
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

    return False

    if args.command == "extract-document":
        output(
            atoms.extract_document(
                args.document_id
            )
        )
        return 0




def register(commands, settings) -> None:
    """Adds this group's subcommands to the parser.

    Registration sits beside the handler for the same command so the two
    cannot drift; they were previously 500 lines apart.
    """
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

    list_atoms = commands.add_parser(
        "list-atoms"
    )
    list_atoms.add_argument("document_id")

    atom = commands.add_parser("atom")
    atom.add_argument("atom_id")

    extract = commands.add_parser(
        "extract-document"
    )
    extract.add_argument("document_id")
