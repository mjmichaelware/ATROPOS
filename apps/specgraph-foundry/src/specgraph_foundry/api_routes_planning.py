"""Plan and export routes.

`Api.dispatch` matched 42 routes in a 972-line try block. The blocks are
independent -- each recognises its own path and returns -- so reading one meant
scrolling past the rest. One module per resource family, each returning None
when the path is not its own.
"""

from __future__ import annotations

from pathlib import Path
import json


def match(api, method, parts, raw_path=None, payload=None):
    """Serves the request if this family owns the path, else returns None."""
    if (
        len(parts) == 3
        and parts[:2] == ["v1", "plans"]
        and method == "GET"
    ):
        return 200, api.planning.get_plan(
            parts[2]
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "plans"]
        and parts[3] == "verify"
        and method == "POST"
    ):
        return 200, api.planning.verify_plan(
            parts[2]
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "plans"]
        and parts[3] == "exports"
        and method == "POST"
    ):
        output_root_value = payload.get(
            "output_root"
        )

        output_root = (
            Path(
                str(output_root_value)
            )
            if output_root_value
            else None
        )

        return 201, api.exports.export_plan(
            parts[2],
            output_root,
        )

    if (
        len(parts) == 3
        and parts[:2] == ["v1", "exports"]
        and method == "GET"
    ):
        return 200, api.exports.get_export(
            parts[2]
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "exports"]
        and parts[3] == "verify"
        and method == "POST"
    ):
        return 200, api.exports.verify_export(
            parts[2]
        )

    if (
        len(parts) == 4
        and parts[:2] == ["v1", "plans"]
        and parts[3] == "execution-runs"
        and method == "POST"
    ):
        export_value = payload.get(
            "export_id"
        )

        return 201, api.execution.start_run(
            plan_id=parts[2],
            runtime_system=str(
                payload.get(
                    "runtime_system",
                    "",
                )
            ),
            runtime_run_id=str(
                payload.get(
                    "runtime_run_id",
                    "",
                )
            ),
            export_id=(
                str(export_value)
                if export_value
                else None
            ),
        )

    return None
