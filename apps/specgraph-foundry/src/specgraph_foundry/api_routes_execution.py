"""Execution run and node routes.

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
        and parts[:2] == ["v1", "execution-runs"]
        and method == "GET"
    ):
        return 200, api.execution.get_run(
            parts[2]
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "execution-runs"]
        and parts[3] == "claim"
        and method == "POST"
    ):
        node_value = payload.get(
            "run_node_id"
        )

        return 200, {
            "claim": api.execution.claim_node(
                run_id=parts[2],
                worker_id=str(
                    payload.get(
                        "worker_id",
                        "",
                    )
                ),
                run_node_id=(
                    str(node_value)
                    if node_value
                    else None
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
        and parts[:2] == ["v1", "execution-runs"]
        and parts[3] == "verify"
        and method == "POST"
    ):
        return 200, api.execution.verify_run(
            parts[2]
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "execution-nodes"]
        and parts[3] == "heartbeat"
        and method == "POST"
    ):
        return 200, api.execution.heartbeat(
            run_node_id=parts[2],
            worker_id=str(
                payload.get(
                    "worker_id",
                    "",
                )
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
        and parts[:2] == ["v1", "execution-nodes"]
        and parts[3] == "receipts"
        and method == "POST"
    ):
        evidence = payload.get(
            "evidence",
            {},
        )

        if not isinstance(evidence, dict):
            raise ValidationError(
                "evidence must be an object"
            )

        return 201, api.execution.submit_receipt(
            run_node_id=parts[2],
            worker_id=str(
                payload.get(
                    "worker_id",
                    "",
                )
            ),
            actor_system=str(
                payload.get(
                    "actor_system",
                    "",
                )
            ),
            outcome=str(
                payload.get(
                    "outcome",
                    "",
                )
            ),
            summary=str(
                payload.get(
                    "summary",
                    "",
                )
            ),
            evidence=evidence,
        )

    return None
