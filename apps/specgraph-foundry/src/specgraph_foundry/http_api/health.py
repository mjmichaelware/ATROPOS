from __future__ import annotations

from ..database import Database


def live_response() -> dict[str, object]:
    return {
        "status": "ok",
        "service": "specgraph-foundry",
    }


def startup_response(database: Database | None = None) -> tuple[int, dict[str, object]]:
    checks = {"configuration": "ready", "schema": "ready"}
    try:
        if database is not None:
            database.health()
    except Exception:
        checks["schema"] = "unavailable"
        return 503, {"status": "unavailable", "service": "specgraph-foundry", "checks": checks}
    return 200, {"status": "ready", "service": "specgraph-foundry", "checks": checks}


def readiness_response(database: Database, *, storage_ready: bool = True, operations_ready: bool = True) -> tuple[int, dict[str, object]]:
    checks = {
        "database": "ready",
        "storage": "ready" if storage_ready else "unavailable",
        "operations": "ready" if operations_ready else "unavailable",
    }
    status = 200
    try:
        database.health()
    except Exception:
        checks["database"] = "unavailable"
        status = 503
    if not storage_ready or not operations_ready:
        status = 503
    return status, {
        "status": "ready" if status == 200 else "unavailable",
        "service": "specgraph-foundry",
        "checks": checks,
    }
