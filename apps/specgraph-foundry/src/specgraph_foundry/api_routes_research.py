"""Research task routes.

`Api.dispatch` matched 42 routes in a 972-line try block. The blocks are
independent -- each recognises its own path and returns -- so reading one meant
scrolling past the rest. One module per resource family, each returning None
when the path is not its own.
"""

from __future__ import annotations

from .errors import ValidationError
import json


def match(api, method, parts, raw_path=None, payload=None):
    """Serves the request if this family owns the path, else returns None."""
    if (
        len(parts) == 3
        and parts[:2] == ["v1", "research-tasks"]
        and method == "GET"
    ):
        return 200, api.research.get_task(parts[2])

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "research-tasks"]
        and parts[3] == "heartbeat"
        and method == "POST"
    ):
        return 200, api.research.heartbeat(
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
        return 201, api.research.add_evidence(
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

        return 200, api.research.complete_task(
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
        return 200, api.research.fail_task(
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

    return None
