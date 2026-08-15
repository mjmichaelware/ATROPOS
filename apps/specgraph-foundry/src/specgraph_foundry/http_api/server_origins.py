"""Which origins may call this server, and which paths are artifact downloads.

Two patterns and one predicate. Their own module because the request path, the
response path and the handler factory all consult them, and each was split out
of the others.
"""

from __future__ import annotations

import re

_VERCEL_PROJECT_DEPLOYMENT_ORIGIN = re.compile(
    r"^https://specgraph-foundry-[a-z0-9]+-mjmichaelwares-projects\.vercel\.app$"
)


_ARTIFACT_DOWNLOAD_RE = re.compile(
    r"^/v1/artifact-downloads/[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+$"
)


def is_origin_allowed(origin: str, allowed_origins: set[str]) -> bool:
    if origin in allowed_origins:
        return True
    return bool(_VERCEL_PROJECT_DEPLOYMENT_ORIGIN.match(origin))
