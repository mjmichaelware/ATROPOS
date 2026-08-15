"""The application version reported in responses.

Its own module because both the dispatcher and the gateway need it, and the
dispatcher was split out of the gateway -- importing it back would be a cycle.
"""

from __future__ import annotations

from importlib.metadata import PackageNotFoundError, version
def application_version() -> str:
    try:
        return version(
            "specgraph-foundry"
        )
    except PackageNotFoundError:
        return "development"
