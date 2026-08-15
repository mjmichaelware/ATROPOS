from __future__ import annotations

from .source_upload_helpers import TERMINAL_UPLOAD_STATUSES
from .source_upload_helpers import *  # noqa: F401,F403
from ..errors import ConflictError, NotFoundError, ValidationError
from .source_upload_errors import SourceUploadSettings
from .source_upload_errors import *  # noqa: F401,F403 - re-exported
from .source_upload_finalize import (
    claim_for_finalization,
    decode_client_provided_bytes,
    finalize,
    mark_failed,
)
from .source_upload_intents import create_intent, get_status, replay_create_intent
from .source_upload_previews import (
    derivation_summary,
    list_project_upload_previews,
    raw_authority_summary,
)

import base64
import binascii
import hashlib
import json
import re
import time
import uuid
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from hmac import compare_digest

from ..database import Database
from ..errors import ConflictError, NotFoundError, ValidationError
from ..ingestion import IngestionService
from .document_adapters import (
    DocumentAdapterRegistry,
)
from .document_security import (
    DOCX_MIME_TYPE,
    HTML_MIME_TYPES,
    PDF_MIME_TYPE,
    TEXT_MIME_TYPES,
    DependencyUnavailableError,
    DocumentEncryptedError,
    DocumentLimitExceededError,
    DocumentSecurityLimits,
    InvalidDocumentError,
    NoExtractableTextError,
    assert_extension_matches_media_type,
    assert_supported_declared_media_type,
    normalized_filename,
    validate_sha256,
)
from .storage import (
    DownloadedObject,
    SignedUploadTarget,
    StorageDependencyError,
    StorageObjectMissingError,
    StorageObjectTooLargeError,
    StorageProtocolError,
    SupabaseStorageClient,
)

























def _utc_now() -> datetime:
    return datetime.now(UTC)


class SourceUploadService:
    def __init__(
        self,
        database: Database,
        storage: SupabaseStorageClient,
        settings: SourceUploadSettings,
        *,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self.database = database
        self.storage = storage
        self.settings = settings
        self._sleep = sleep
        self.ingestion = IngestionService(database)
        self.security_limits = DocumentSecurityLimits(
            max_original_bytes=settings.max_source_bytes
        )
        self.adapters = DocumentAdapterRegistry(
            self.security_limits
        )

    def create_intent(
        self,
        *,
        owner_id: str,
        authorization: str,
        project_id: str,
        upload_id: str,
        filename: str,
        media_type: str,
        byte_size: int,
        sha256: str,
    ) -> dict[str, object]:
        """Delegates to :func:`source_upload_intents.create_intent`."""
        return create_intent(
            self,
            owner_id=owner_id,
            authorization=authorization,
            project_id=project_id,
            upload_id=upload_id,
            filename=filename,
            media_type=media_type,
            byte_size=byte_size,
            sha256=sha256,
        )


    def replay_create_intent(
        self,
        *,
        owner_id: str,
        authorization: str,
        upload_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`source_upload_intents.replay_create_intent`."""
        return replay_create_intent(
            self,
            owner_id=owner_id,
            authorization=authorization,
            upload_id=upload_id,
        )


    def get_status(
        self,
        *,
        owner_id: str,
        upload_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`source_upload_intents.get_status`."""
        return get_status(
            self,
            owner_id=owner_id,
            upload_id=upload_id,
        )


    def finalize(
        self,
        *,
        owner_id: str,
        authorization: str,
        upload_id: str,
        raw_base64: str | None = None,
    ) -> dict[str, object]:
        """Delegates to :func:`source_upload_finalize.finalize`."""
        return finalize(
            self,
            owner_id=owner_id,
            authorization=authorization,
            upload_id=upload_id,
            raw_base64=raw_base64,
        )


    def list_project_upload_previews(
        self,
        *,
        owner_id: str,
        project_id: str,
    ) -> tuple[list[dict[str, object]], int]:
        """Delegates to :func:`source_upload_previews.list_project_upload_previews`."""
        return list_project_upload_previews(
            self,
            owner_id=owner_id,
            project_id=project_id,
        )


    def _claim_for_finalization(
        self,
        *,
        owner_id: str,
        upload_id: str,
    ):
        """Delegates to :func:`source_upload_finalize.claim_for_finalization`."""
        return claim_for_finalization(
            self,
            owner_id=owner_id,
            upload_id=upload_id,
        )


    # Supabase Storage's backend can briefly report a just-uploaded object
    # as missing before it is consistently readable through its
    # authenticated download route, even well after the upload PUT and
    # its ObjectCreated lifecycle event have completed. Retry a bounded
    # number of times before surfacing the failure - the total delay
    # stays well within SPECGRAPH_REQUEST_DEADLINE_SECONDS.
    _DOWNLOAD_RETRY_DELAYS_SECONDS = (0.0, 1.0, 2.0, 4.0, 4.0)


    def _decode_client_provided_bytes(
        self,
        raw_base64: str,
        upload_id: str,
    ) -> DownloadedObject:
        """Delegates to :func:`source_upload_finalize.decode_client_provided_bytes`."""
        return decode_client_provided_bytes(
            self,
            raw_base64,
            upload_id,
        )



    def _mark_failed(
        self,
        upload_id: str,
        failure_code: str,
        *,
        actual_bytes: int | None = None,
        actual_sha256: str | None = None,
    ) -> None:
        """Delegates to :func:`source_upload_finalize.mark_failed`."""
        return mark_failed(
            self,
            upload_id,
            failure_code,
            actual_bytes=actual_bytes,
            actual_sha256=actual_sha256,
        )










    @staticmethod
    def _parse_time(
        value: str,
    ) -> datetime:
        parsed = datetime.fromisoformat(value)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=UTC)
        return parsed.astimezone(UTC)


    def _raw_authority_summary(
        self,
        upload_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`source_upload_previews.raw_authority_summary`."""
        return raw_authority_summary(
            self,
            upload_id,
        )


    def _derivation_summary(
        self,
        upload_id: str,
    ) -> dict[str, object]:
        """Delegates to :func:`source_upload_previews.derivation_summary`."""
        return derivation_summary(
            self,
            upload_id,
        )

