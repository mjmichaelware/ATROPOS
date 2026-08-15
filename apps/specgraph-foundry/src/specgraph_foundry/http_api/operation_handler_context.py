"""The context an operation handler runs in, and its failure signal.

Carries the services a handler needs plus the checkpoint callback that lets a
long operation report progress without knowing how progress is stored.
"""

from __future__ import annotations

import json
from dataclasses import dataclass

@dataclass(frozen=True)
class HandlerContext:
    owner_id: str
    database: RequestScopedDatabase
    operations: OperationStore
    lease: OperationLease
    durable_exports: DurableExportService | None = None
    source_uploads: SourceUploadService | None = None
    authorization: str = "Bearer worker"

    def checkpoint(
        self,
        phase: str,
        current: int,
        total: int,
    ) -> None:
        try:
            self.operations.progress(
                self.lease,
                phase=phase,
                current=current,
                total=total,
            )
        except (WorkerLeaseLost, OperationCancelled):
            raise
        except Exception:
            # Transient DB error (e.g. psycopg.OperationalError on a slow or
            # flaky connection). The background heartbeat thread keeps the
            # lease alive independently; progress updates are best-effort.
            pass


class DependencyUnavailable(RuntimeError):
    pass
