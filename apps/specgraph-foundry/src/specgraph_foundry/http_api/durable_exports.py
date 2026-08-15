from __future__ import annotations

from .durable_export_helpers import mark_invalid
from .durable_export_helpers import mark_verified
from .durable_export_read import (
    build_manifest,
    download,
    get_export,
    manifest_summary,
    read_artifacts,
    verify_export,
)
from .durable_export_models import ArtifactNotVerifiedError
from .durable_export_write import export_plan, persist_initial, mark_objects
from .durable_export_models import *  # noqa: F401,F403
from .durable_export_helpers import *  # noqa: F401,F403
from ..errors import NotFoundError, ValidationError
import tempfile

import json
import shutil
import tempfile
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Callable

from ..database import Database
from ..errors import NotFoundError, ValidationError
from ..execution import ExecutionService
from ..exports import ExportService, canonical_json_bytes, sha256_file
from .artifact_storage import (
    ArtifactAlreadyExistsError,
    ArtifactIntegrityError,
    ArtifactStorageClient,
    ArtifactStoragePermanentError,
    ArtifactStorageUnavailableError,
    StoredArtifact,
    artifact_object_path,
    media_type_for_name,
    sha256_bytes,
    validate_artifact_name,
)
from .operations import OperationCancelled








def utc_now() -> str:
    return datetime.now(UTC).isoformat()




class DurableExportService:
    def __init__(
        self,
        database: Database,
        storage: ArtifactStorageClient,
    ) -> None:
        self.database = database
        self.storage = storage
        self.exports = ExportService(database)
        self.execution = ExecutionService(database)

    def export_plan(
        self,
        *,
        owner_id: str,
        authorization: str,
        plan_id: str,
        on_progress: Callable[[int, int], None] | None = None,
    ) -> dict[str, object]:
        """Delegates to :func:`durable_export_write.export_plan`."""
        return export_plan(
            self,
            owner_id=owner_id,
            authorization=authorization,
            plan_id=plan_id,
            on_progress=on_progress,
        )


    def verify_export(
        self,
        *,
        owner_id: str,
        authorization: str,
        export_id: str,
        on_progress: Callable[[int, int], None] | None = None,
    ) -> dict[str, object]:
        """Delegates to :func:`durable_export_read.verify_export`."""
        return verify_export(
            self,
            owner_id=owner_id,
            authorization=authorization,
            export_id=export_id,
            on_progress=on_progress,
        )


    def get_export(
        self,
        export_id: str,
        owner_id: str | None = None,
    ) -> dict[str, object]:
        """Delegates to :func:`durable_export_read.get_export`."""
        return get_export(
            self,
            export_id,
            owner_id,
        )


    def list_exports(
        self,
        project_id: str,
    ) -> list[dict[str, object]]:
        items = self.exports.list_exports(project_id)
        for item in items:
            item["output_path"] = None
            try:
                item["artifact_manifest"] = self._manifest_summary(
                    str(item["id"])
                )
            except NotFoundError:
                item["artifact_manifest"] = None
        return items

    def download(
        self,
        *,
        owner_id: str,
        authorization: str,
        export_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`durable_export_read.download`."""
        return download(
            self,
            owner_id=owner_id,
            authorization=authorization,
            export_id=export_id,
        )


    def assert_execution_ready(
        self,
        *,
        owner_id: str,
        export_id: str | None,
    ) -> None:
        if export_id is None:
            return
        manifest = manifest_row(self, 
            owner_id=owner_id,
            export_id=export_id,
        )
        if str(manifest["state"]) != "VERIFIED":
            raise ArtifactNotVerifiedError(
                "artifact manifest is not verified"
            )

    def start_execution_run(
        self,
        *,
        owner_id: str,
        plan_id: str,
        runtime_system: str,
        runtime_run_id: str,
        export_id: str | None,
    ) -> dict[str, object]:
        self.assert_execution_ready(
            owner_id=owner_id,
            export_id=export_id,
        )
        return self.execution.start_run(
            plan_id=plan_id,
            runtime_system=runtime_system,
            runtime_run_id=runtime_run_id,
            export_id=export_id,
        )

    def _read_artifacts(
        self,
        output_path: Path,
        *,
        owner_id: str,
        project_id: str,
        export_id: str,
    ) -> list[StoredArtifact]:
        """Delegates to :func:`durable_export_read.read_artifacts`."""
        return read_artifacts(
            self,
            output_path,
            owner_id=owner_id,
            project_id=project_id,
            export_id=export_id,
        )


    def _manifest(
        self,
        *,
        export: dict[str, object],
        artifacts: list[StoredArtifact],
    ) -> dict[str, object]:
        """Delegates to :func:`durable_export_read.manifest`."""
        return build_manifest(
            self,
            export=export,
            artifacts=artifacts,
        )


    def _persist_initial(
        self,
        *,
        owner_id: str,
        project_id: str,
        export_id: str,
        artifacts: list[StoredArtifact],
        manifest: dict[str, object],
    ) -> None:
        """Delegates to :func:`durable_export_write.persist_initial`."""
        return persist_initial(
            self,
            owner_id=owner_id,
            project_id=project_id,
            export_id=export_id,
            artifacts=artifacts,
            manifest=manifest,
        )


    def _mark_objects(
        self,
        export_id: str,
        state: str,
    ) -> None:
        """Delegates to :func:`durable_export_write.mark_objects`."""
        return mark_objects(
            self,
            export_id,
            state,
        )


    def _mark_verified(
        self,
        export_id: str,
    ) -> None:
        """Delegates to :func:`durable_export_write.mark_verified`."""
        return mark_verified(
            self,
            export_id,
        )


    def _mark_invalid(
        self,
        export_id: str,
    ) -> None:
        """Delegates to :func:`durable_export_write.mark_invalid`."""
        return mark_invalid(
            self,
            export_id,
        )




    def _manifest_summary(
        self,
        export_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`durable_export_read.manifest_summary`."""
        return manifest_summary(
            self,
            export_id,
        )


