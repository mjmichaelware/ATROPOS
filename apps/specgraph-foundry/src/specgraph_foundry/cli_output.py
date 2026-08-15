"""How the CLI prints a result.

One function, shared by every command group. It lives outside :mod:`cli` because
cli imports the groups and the groups need this -- importing it back from cli
would be a cycle.
"""

from __future__ import annotations

import json

def output(value: object) -> None:
    print(
        json.dumps(
            value,
            indent=2,
            sort_keys=True,
        )
    )
