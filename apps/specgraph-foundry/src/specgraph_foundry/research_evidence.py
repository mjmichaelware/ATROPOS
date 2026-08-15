"""Recording evidence against a claimed task.

Evidence is what turns an assertion into a finding, so this is the write path
that decides whether a dimension can ever be closed.
"""

from __future__ import annotations

from .research_events import record_research_event
from .research_vocabulary import EVIDENCE_TYPES
from .primitives import new_id, utc_now
from .research_leases import require_lease
import hashlib
import json
import sqlite3

from .database import Database
from .errors import ConflictError, NotFoundError, ValidationError
from .primitives import canonical_json, new_id, parse_time, utc_now, utc_now_datetime



def add_evidence(
    database: Database,
    task_id: str,
    worker_id: str,
    source_uri: str,
    source_title: str,
    excerpt: str,
    publisher: str = "",
    evidence_type: str = "OTHER",
    reliability: float = 0.5,
) -> dict[str, object]:
    source_uri = source_uri.strip()
    source_title = source_title.strip()
    excerpt = excerpt.strip()
    evidence_type = evidence_type.strip().upper()

    if not source_uri:
        raise ValidationError("source_uri is required")

    if not source_title:
        raise ValidationError("source_title is required")

    if not excerpt:
        raise ValidationError("excerpt is required")

    if evidence_type not in EVIDENCE_TYPES:
        raise ValidationError(
            f"invalid evidence type: {evidence_type}"
        )

    if not 0.0 <= reliability <= 1.0:
        raise ValidationError(
            "reliability must be between 0 and 1"
        )

    evidence_id = new_id("evidence")
    digest = hashlib.sha256(
        excerpt.encode("utf-8")
    ).hexdigest()
    timestamp = utc_now()

    try:
        with database.connect() as connection:
            task = require_lease(
                connection,
                task_id,
                worker_id,
            )

            connection.execute(
                """
                INSERT INTO research_evidence(
                    id,
                    project_id,
                    task_id,
                    atom_id,
                    dimension,
                    source_uri,
                    source_title,
                    publisher,
                    evidence_type,
                    excerpt,
                    content_sha256,
                    reliability,
                    retrieved_at,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    evidence_id,
                    task["project_id"],
                    task_id,
                    task["atom_id"],
                    task["dimension"],
                    source_uri,
                    source_title,
                    publisher.strip(),
                    evidence_type,
                    excerpt,
                    digest,
                    reliability,
                    timestamp,
                    timestamp,
                ),
            )

            record_research_event(
                connection,
                task_id,
                "EVIDENCE_ADDED",
                worker_id,
                {
                    "evidence_id": evidence_id,
                    "source_uri": source_uri,
                },
            )

    except sqlite3.IntegrityError as error:
        raise ConflictError(
            "identical evidence already exists"
        ) from error

    return get_evidence(database, evidence_id)


def get_evidence(
    database: Database,
    evidence_id: str,
) -> dict[str, object]:
    with database.connect() as connection:
        row = connection.execute(
            """
            SELECT *
            FROM research_evidence
            WHERE id = ?
            """,
            (evidence_id,),
        ).fetchone()

    if row is None:
        raise NotFoundError(
            f"evidence not found: {evidence_id}"
        )

    return dict(row)
