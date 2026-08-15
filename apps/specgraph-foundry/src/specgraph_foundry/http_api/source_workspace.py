from .source_workspace_helpers import *  # noqa: F401,F403 - re-exported
from ..errors import NotFoundError
from .source_workspace_document import get_document, upload_status
from .source_workspace_project import get_project
import json
from datetime import UTC, datetime
from typing import Any

from ..database import Database
from ..errors import NotFoundError
from .pagination import WORKSPACE_PREVIEW_LIMIT




class SourceWorkspaceService:
    def __init__(
        self,
        database: Database,
    ) -> None:
        self.database = database

    def get_project(
        self,
        project_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`source_workspace_project.get_project`."""
        return get_project(
            self,
            project_id,
        )


    def get_document(
        self,
        document_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`source_workspace_document.get_document`."""
        return get_document(
            self,
            document_id,
        )







    @staticmethod
    def _upload_status(
        upload: dict[str, object],
    ) -> str:
        """Delegates to :func:`source_workspace_document.upload_status`."""
        return upload_status(
            upload,
        )

