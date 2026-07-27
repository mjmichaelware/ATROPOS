from __future__ import annotations

import hashlib
import json
import re
import uuid
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


SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
WORKSPACE_PREVIEW_LIMIT = 5
PLACEHOLDER_SIGNING_KEY = "replace-with-a-long-random-secret"
FINALIZING_TTL_SECONDS = 900

TERMINAL_UPLOAD_STATUSES = {
    "FINALIZED",
    "FAILED",
    "EXPIRED",
}


class UploadDependencyUnavailableError(
    RuntimeError
):
    pass


class UploadExpiredError(RuntimeError):
    pass


class UploadStateConflictError(
    RuntimeError
):
    pass


class UploadIntegrityMismatchError(
    RuntimeError
):
    pass


class InvalidSourceEncodingError(
    RuntimeError
):
    pass


class InvalidDocumentUploadError(
    RuntimeError
):
    pass


class DocumentEncryptedUploadError(
    RuntimeError
):
    pass


class DocumentLimitExceededUploadError(
    RuntimeError
):
    pass


class NoExtractableTextUploadError(
    RuntimeError
):
    pass


@dataclass(frozen=True)
class SourceUploadSettings:
    bucket: str
    upload_url_ttl_seconds: int
    max_source_bytes: int

    def __post_init__(self) -> None:
        if not self.bucket.strip():
            raise ValueError(
                "source bucket is required"
            )

        if self.upload_url_ttl_seconds < 30:
            raise ValueError(
                "upload URL TTL must be at least 30 seconds"
            )

        if self.max_source_bytes < 1:
            raise ValueError(
                "maximum source bytes must be positive"
            )


def _utc_now() -> datetime:
    return datetime.now(UTC)


class SourceUploadService:
    def __init__(
        self,
        database: Database,
        storage: SupabaseStorageClient,
        settings: SourceUploadSettings,
    ) -> None:
        self.database = database
        self.storage = storage
        self.settings = settings
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
        try:
            original_filename = normalized_filename(
                filename
            )
        except InvalidDocumentError as error:
            raise ValidationError(str(error)) from error
        declared_media_type = self._normalize_media_type(
            media_type
        )
        self._validate_media_type(
            original_filename,
            declared_media_type,
        )
        expected_bytes = self._validate_byte_size(
            byte_size
        )
        try:
            expected_sha256 = validate_sha256(
                sha256
            )
        except InvalidDocumentError as error:
            raise ValidationError(str(error)) from error
        object_path = (
            f"{owner_id}/{project_id}/{upload_id}/source"
        )
        now = _utc_now().isoformat()

        with self.database.connect() as connection:
            project = connection.execute(
                """
                SELECT id
                FROM projects
                WHERE id = ?
                """,
                (project_id,),
            ).fetchone()

            if project is None:
                raise NotFoundError(
                    f"project not found: {project_id}"
                )

            existing = connection.execute(
                """
                SELECT *
                FROM source_uploads
                WHERE id = ?
                """,
                (upload_id,),
            ).fetchone()

            if existing is None:
                connection.execute(
                    """
                    INSERT INTO source_uploads(
                        id,
                        owner_id,
                        project_id,
                        bucket,
                        object_path,
                        original_filename,
                        declared_media_type,
                        expected_bytes,
                        expected_sha256,
                        status,
                        created_at,
                        updated_at,
                        expires_at
                    )
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    (
                        upload_id,
                        owner_id,
                        project_id,
                        self.settings.bucket,
                        object_path,
                        original_filename,
                        declared_media_type,
                        expected_bytes,
                        expected_sha256,
                        "PENDING",
                        now,
                        now,
                        now,
                    ),
                )
            else:
                row = dict(existing)
                if (
                    str(row["owner_id"]) != owner_id
                    or str(row["project_id"])
                    != project_id
                    or str(row["object_path"])
                    != object_path
                    or str(row["declared_media_type"])
                    != declared_media_type
                    or int(row["expected_bytes"])
                    != expected_bytes
                    or str(row["expected_sha256"])
                    != expected_sha256
                    or str(row["original_filename"])
                    != original_filename
                ):
                    raise ConflictError(
                        "upload intent cannot be reused for a different request"
                    )

        signed = self._signed_target(
            authorization=authorization,
            bucket=self.settings.bucket,
            object_path=object_path,
        )
        expires_at = signed.expires_at

        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE source_uploads
                SET status = CASE
                    WHEN status = 'FINALIZED'
                    THEN status
                    ELSE 'PENDING'
                END,
                    updated_at = ?,
                    expires_at = ?
                WHERE id = ?
                """,
                (now, expires_at, upload_id),
            )

        return self._create_intent_response(
            upload_id=upload_id,
            project_id=project_id,
            status="PENDING",
            bucket=self.settings.bucket,
            object_path=object_path,
            original_filename=original_filename,
            declared_media_type=declared_media_type,
            expected_bytes=expected_bytes,
            signed=signed,
        )

    def replay_create_intent(
        self,
        *,
        owner_id: str,
        authorization: str,
        upload_id: str,
    ) -> dict[str, object]:
        row = self._get_owned_upload(
            owner_id=owner_id,
            upload_id=upload_id,
        )
        signed = self._signed_target(
            authorization=authorization,
            bucket=str(row["bucket"]),
            object_path=str(row["object_path"]),
        )
        now = _utc_now().isoformat()

        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE source_uploads
                SET updated_at = ?,
                    expires_at = ?
                WHERE id = ?
                """,
                (
                    now,
                    signed.expires_at,
                    upload_id,
                ),
            )

        return self._create_intent_response(
            upload_id=str(row["id"]),
            project_id=str(row["project_id"]),
            status=self._display_status(dict(row)),
            bucket=str(row["bucket"]),
            object_path=str(row["object_path"]),
            original_filename=str(
                row["original_filename"]
            ),
            declared_media_type=str(
                row["declared_media_type"]
            ),
            expected_bytes=int(row["expected_bytes"]),
            signed=signed,
        )

    def get_status(
        self,
        *,
        owner_id: str,
        upload_id: str,
    ) -> dict[str, object]:
        row = dict(
            self._get_owned_upload(
                owner_id=owner_id,
                upload_id=upload_id,
            )
        )
        body = {
            "id": str(row["id"]),
            "project_id": str(row["project_id"]),
            "status": self._display_status(row),
            "bucket": str(row["bucket"]),
            "object_path": str(row["object_path"]),
            "filename": str(row["original_filename"]),
            "media_type": str(
                row["declared_media_type"]
            ),
            "byte_size": int(row["expected_bytes"]),
            "failure_code": (
                str(row["failure_code"])
                if row["failure_code"]
                else None
            ),
            "created_at": str(row["created_at"]),
            "updated_at": str(row["updated_at"]),
            "expires_at": str(row["expires_at"]),
            "finalized_at": (
                str(row["finalized_at"])
                if row["finalized_at"] is not None
                else None
            ),
        }

        if row["document_id"] is not None:
            document_id = str(row["document_id"])
            body["document_id"] = document_id
            body["document_route"] = (
                f"/v1/documents/{document_id}"
            )

        return body

    def finalize(
        self,
        *,
        owner_id: str,
        authorization: str,
        upload_id: str,
    ) -> dict[str, object]:
        row = self._claim_for_finalization(
            owner_id=owner_id,
            upload_id=upload_id,
        )
        upload = dict(row)

        if upload["document_id"] is not None:
            return self._finalized_response(
                upload_id=upload_id,
                document_id=str(upload["document_id"]),
                upload_status="FINALIZED",
            )

        try:
            downloaded = self.storage.download_object(
                authorization=authorization,
                bucket=str(upload["bucket"]),
                object_path=str(upload["object_path"]),
                max_bytes=self.settings.max_source_bytes,
            )
        except StorageObjectMissingError as error:
            self._restore_pending(upload_id)
            raise UploadStateConflictError(
                "upload bytes are not available"
            ) from error
        except StorageObjectTooLargeError as error:
            self._mark_failed(
                upload_id,
                "SOURCE_TOO_LARGE",
            )
            raise ValidationError(
                "source exceeds the configured maximum"
            ) from error
        except (
            StorageDependencyError,
            StorageProtocolError,
        ) as error:
            self._restore_pending(upload_id)
            raise UploadDependencyUnavailableError(
                "storage dependency is unavailable"
            ) from error

        expected_bytes = int(upload["expected_bytes"])
        expected_sha256 = str(upload["expected_sha256"])
        actual_sha256 = hashlib.sha256(
            downloaded.data
        ).hexdigest()
        actual_bytes = len(downloaded.data)

        if actual_bytes != expected_bytes or not compare_digest(
            actual_sha256,
            expected_sha256,
        ):
            self._mark_failed(
                upload_id,
                "UPLOAD_INTEGRITY_MISMATCH",
                actual_bytes=actual_bytes,
                actual_sha256=actual_sha256,
            )
            raise UploadIntegrityMismatchError(
                "uploaded source failed integrity verification"
            )

        declared_media_type = str(
            upload["declared_media_type"]
        )
        storage_media_type = (
            self._normalize_media_type(
                downloaded.media_type
            )
            if downloaded.media_type
            else None
        )

        try:
            derivation = self.adapters.derive_text(
                filename=str(upload["original_filename"]),
                declared_media_type=declared_media_type,
                storage_media_type=storage_media_type,
                raw=downloaded.data,
            )
        except InvalidDocumentError as error:
            if "UTF-8" in str(error) or "binary content" in str(error):
                self._mark_failed(
                    upload_id,
                    "INVALID_SOURCE_ENCODING",
                    actual_bytes=actual_bytes,
                    actual_sha256=actual_sha256,
                )
                raise InvalidSourceEncodingError(
                    str(error)
                ) from error
            self._mark_failed(
                upload_id,
                "INVALID_DOCUMENT",
                actual_bytes=actual_bytes,
                actual_sha256=actual_sha256,
            )
            raise InvalidDocumentUploadError(
                str(error)
            ) from error
        except DocumentEncryptedError as error:
            self._mark_failed(
                upload_id,
                "DOCUMENT_ENCRYPTED",
                actual_bytes=actual_bytes,
                actual_sha256=actual_sha256,
            )
            raise DocumentEncryptedUploadError(
                str(error)
            ) from error
        except DocumentLimitExceededError as error:
            self._mark_failed(
                upload_id,
                "DOCUMENT_LIMIT_EXCEEDED",
                actual_bytes=actual_bytes,
                actual_sha256=actual_sha256,
            )
            raise DocumentLimitExceededUploadError(
                str(error)
            ) from error
        except NoExtractableTextError as error:
            self._mark_failed(
                upload_id,
                "NO_EXTRACTABLE_TEXT",
                actual_bytes=actual_bytes,
                actual_sha256=actual_sha256,
            )
            raise NoExtractableTextUploadError(
                str(error)
            ) from error
        except DependencyUnavailableError as error:
            self._restore_pending(upload_id)
            raise UploadDependencyUnavailableError(
                "document adapter dependency is unavailable"
            ) from error

        derived_bytes = derivation.derived_bytes
        derived_sha256 = hashlib.sha256(
            derived_bytes
        ).hexdigest()

        try:
            document = self.ingestion.ingest_uploaded_bytes(
                project_id=str(upload["project_id"]),
                title=str(upload["original_filename"]),
                raw=derived_bytes,
                media_type=declared_media_type,
                source_upload_id=upload_id,
            )
        except ConflictError:
            document = self._existing_document(
                project_id=str(upload["project_id"]),
                sha256=derived_sha256,
            )
        except ValidationError as error:
            self._mark_failed(
                upload_id,
                "INVALID_DOCUMENT",
                actual_bytes=actual_bytes,
                actual_sha256=actual_sha256,
            )
            raise InvalidDocumentUploadError(
                str(error)
            ) from error

        now = _utc_now().isoformat()

        with self.database.connect() as connection:
            connection.execute(
                """
                INSERT INTO document_derivations(
                    id,
                    owner_id,
                    project_id,
                    source_upload_id,
                    source_document_id,
                    adapter_name,
                    adapter_version,
                    original_media_type,
                    detected_media_type,
                    original_byte_count,
                    original_sha256,
                    derived_byte_count,
                    derived_sha256,
                    status,
                    metadata_json,
                    created_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(source_upload_id) DO UPDATE SET
                    source_document_id = excluded.source_document_id,
                    adapter_name = excluded.adapter_name,
                    adapter_version = excluded.adapter_version,
                    original_media_type = excluded.original_media_type,
                    detected_media_type = excluded.detected_media_type,
                    original_byte_count = excluded.original_byte_count,
                    original_sha256 = excluded.original_sha256,
                    derived_byte_count = excluded.derived_byte_count,
                    derived_sha256 = excluded.derived_sha256,
                    status = excluded.status,
                    metadata_json = excluded.metadata_json,
                    created_at = excluded.created_at
                """,
                (
                    str(uuid.uuid4()),
                    owner_id,
                    str(upload["project_id"]),
                    upload_id,
                    str(document["id"]),
                    derivation.adapter_name,
                    derivation.adapter_version,
                    declared_media_type,
                    derivation.detected_media_type,
                    actual_bytes,
                    actual_sha256,
                    len(derived_bytes),
                    derived_sha256,
                    "SUCCEEDED",
                    json.dumps(
                        derivation.metadata,
                        sort_keys=True,
                        separators=(",", ":"),
                        ensure_ascii=False,
                    ),
                    now,
                ),
            )
            connection.execute(
                """
                UPDATE source_uploads
                SET status = 'FINALIZED',
                    actual_bytes = ?,
                    actual_sha256 = ?,
                    document_id = ?,
                    failure_code = NULL,
                    updated_at = ?,
                    finalized_at = ?
                WHERE id = ?
                """,
                (
                    actual_bytes,
                    actual_sha256,
                    str(document["id"]),
                    now,
                    now,
                    upload_id,
                ),
            )

        return self._finalized_response(
            upload_id=upload_id,
            document_id=str(document["id"]),
            upload_status="FINALIZED",
        )

    def list_project_upload_previews(
        self,
        *,
        owner_id: str,
        project_id: str,
    ) -> tuple[list[dict[str, object]], int]:
        with self.database.connect() as connection:
            total = int(
                connection.execute(
                    """
                    SELECT COUNT(*) AS value
                    FROM source_uploads
                    WHERE owner_id = ?
                      AND project_id = ?
                    """,
                    (owner_id, project_id),
                ).fetchone()["value"]
            )
            rows = connection.execute(
                """
                SELECT
                    id,
                    status,
                    original_filename,
                    declared_media_type,
                    expected_bytes,
                    failure_code,
                    created_at,
                    updated_at,
                    expires_at,
                    document_id
                FROM source_uploads
                WHERE owner_id = ?
                  AND project_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """,
                (
                    owner_id,
                    project_id,
                    WORKSPACE_PREVIEW_LIMIT,
                ),
            ).fetchall()

        items: list[dict[str, object]] = []

        for row in rows:
            upload = dict(row)
            item = {
                "id": str(upload["id"]),
                "status": self._display_status(upload),
                "filename": str(
                    upload["original_filename"]
                ),
                "media_type": str(
                    upload["declared_media_type"]
                ),
                "byte_size": int(
                    upload["expected_bytes"]
                ),
                "failure_code": (
                    str(upload["failure_code"])
                    if upload["failure_code"]
                    else None
                ),
                "created_at": str(
                    upload["created_at"]
                ),
                "updated_at": str(
                    upload["updated_at"]
                ),
                "detail_route": (
                    f"/v1/source-uploads/{upload['id']}"
                ),
            }

            if upload["document_id"] is not None:
                item["document_id"] = str(
                    upload["document_id"]
                )
                item["document_route"] = (
                    f"/v1/documents/{upload['document_id']}"
                )

            items.append(item)

        return items, total

    def _claim_for_finalization(
        self,
        *,
        owner_id: str,
        upload_id: str,
    ):
        now = _utc_now()
        now_text = now.isoformat()
        expires_at = (
            now
            + timedelta(
                seconds=FINALIZING_TTL_SECONDS
            )
        ).isoformat()

        while True:
            with self.database.connect() as connection:
                row = connection.execute(
                    """
                    SELECT *
                    FROM source_uploads
                    WHERE id = ?
                      AND owner_id = ?
                    """,
                    (upload_id, owner_id),
                ).fetchone()

                if row is None:
                    raise NotFoundError(
                        f"source upload not found: {upload_id}"
                    )

                upload = dict(row)
                status = self._display_status(upload)

                if status == "EXPIRED":
                    connection.execute(
                        """
                        UPDATE source_uploads
                        SET status = 'EXPIRED',
                            updated_at = ?
                        WHERE id = ?
                        """,
                        (now_text, upload_id),
                    )
                    raise UploadExpiredError(
                        "upload intent has expired"
                    )

                if status == "FINALIZED":
                    return row

                if status == "FAILED":
                    raise UploadStateConflictError(
                        "upload cannot be finalized"
                    )

                if status == "FINALIZING":
                    stale = (
                        self._parse_time(
                            str(upload["expires_at"])
                        )
                        <= now
                    )
                    if not stale:
                        raise UploadStateConflictError(
                            "upload finalization is already in progress"
                        )

                updated = connection.execute(
                    """
                    UPDATE source_uploads
                    SET status = 'FINALIZING',
                        updated_at = ?,
                        expires_at = ?
                    WHERE id = ?
                      AND owner_id = ?
                      AND (
                        status IN ('PENDING', 'UPLOADED')
                        OR (
                            status = 'FINALIZING'
                            AND expires_at <= ?
                        )
                      )
                    """,
                    (
                        now_text,
                        expires_at,
                        upload_id,
                        owner_id,
                        now_text,
                    ),
                )

                if updated.rowcount != 1:
                    continue

                refreshed = connection.execute(
                    """
                    SELECT *
                    FROM source_uploads
                    WHERE id = ?
                      AND owner_id = ?
                    """,
                    (upload_id, owner_id),
                ).fetchone()

                if refreshed is None:
                    raise NotFoundError(
                        f"source upload not found: {upload_id}"
                    )

                return refreshed

    def _restore_pending(
        self,
        upload_id: str,
    ) -> None:
        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE source_uploads
                SET status = 'PENDING',
                    updated_at = ?
                WHERE id = ?
                """,
                (_utc_now().isoformat(), upload_id),
            )

    def _mark_failed(
        self,
        upload_id: str,
        failure_code: str,
        *,
        actual_bytes: int | None = None,
        actual_sha256: str | None = None,
    ) -> None:
        now = _utc_now().isoformat()
        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE source_uploads
                SET status = 'FAILED',
                    actual_bytes = ?,
                    actual_sha256 = ?,
                    failure_code = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                (
                    actual_bytes,
                    actual_sha256,
                    failure_code,
                    now,
                    upload_id,
                ),
            )

    def _existing_document(
        self,
        *,
        project_id: str,
        sha256: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT id
                FROM source_documents
                WHERE project_id = ?
                  AND sha256 = ?
                """,
                (project_id, sha256),
            ).fetchone()

        if row is None:
            raise ConflictError(
                "source already exists in this project"
            )

        return self.ingestion.get_document(
            str(row["id"])
        )

    def _get_owned_upload(
        self,
        *,
        owner_id: str,
        upload_id: str,
    ):
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT *
                FROM source_uploads
                WHERE id = ?
                  AND owner_id = ?
                """,
                (upload_id, owner_id),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"source upload not found: {upload_id}"
            )

        return row

    def _create_intent_response(
        self,
        *,
        upload_id: str,
        project_id: str,
        status: str,
        bucket: str,
        object_path: str,
        original_filename: str,
        declared_media_type: str,
        expected_bytes: int,
        signed: SignedUploadTarget,
    ) -> dict[str, object]:
        return {
            "id": upload_id,
            "project_id": project_id,
            "status": status,
            "bucket": bucket,
            "object_path": object_path,
            "filename": original_filename,
            "media_type": declared_media_type,
            "byte_size": expected_bytes,
            "signed_upload_url": signed.url,
            "signed_url_expires_at": signed.expires_at,
            "required_upload_headers": signed.required_headers,
        }

    def _finalized_response(
        self,
        *,
        upload_id: str,
        document_id: str,
        upload_status: str,
    ) -> dict[str, object]:
        return {
            "upload_id": upload_id,
            "status": upload_status,
            "document_id": document_id,
            "document_route": f"/v1/documents/{document_id}",
            "document": self.ingestion.get_document(
                document_id
            ),
            "raw_authority": self._raw_authority_summary(
                upload_id
            ),
            "derivation": self._derivation_summary(
                upload_id
            ),
        }

    def _signed_target(
        self,
        *,
        authorization: str,
        bucket: str,
        object_path: str,
    ) -> SignedUploadTarget:
        return self.storage.create_signed_upload_target(
            authorization=authorization,
            bucket=bucket,
            object_path=object_path,
            ttl_seconds=self.settings.upload_url_ttl_seconds,
        )

    def _validate_byte_size(
        self,
        value: int,
    ) -> int:
        if value <= 0:
            raise ValidationError(
                "byte_size must be positive"
            )

        if value > self.settings.max_source_bytes:
            raise ValidationError(
                "source exceeds the configured maximum"
            )

        return value

    @staticmethod
    def _normalize_media_type(
        value: str,
    ) -> str:
        primary = str(value).split(";", 1)[0]
        normalized = primary.strip().lower()

        if not normalized:
            raise ValidationError(
                "media_type is required"
            )

        return normalized

    @staticmethod
    def _validate_media_type(
        filename: str,
        media_type: str,
    ) -> None:
        try:
            assert_supported_declared_media_type(
                media_type
            )
            assert_extension_matches_media_type(
                filename,
                media_type,
            )
        except InvalidDocumentError as error:
            raise ValidationError(str(error)) from error

    @staticmethod
    def _parse_time(
        value: str,
    ) -> datetime:
        parsed = datetime.fromisoformat(value)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=UTC)
        return parsed.astimezone(UTC)

    def _display_status(
        self,
        row: dict[str, object],
    ) -> str:
        status = str(row["status"])

        if status in TERMINAL_UPLOAD_STATUSES:
            return status

        if self._parse_time(
            str(row["expires_at"])
        ) <= _utc_now():
            return "EXPIRED"

        return status

    def _raw_authority_summary(
        self,
        upload_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT
                    id,
                    bucket,
                    object_path,
                    declared_media_type,
                    actual_bytes,
                    actual_sha256
                FROM source_uploads
                WHERE id = ?
                """,
                (upload_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"source upload not found: {upload_id}"
            )

        return {
            "source_upload_id": str(row["id"]),
            "bucket": str(row["bucket"]),
            "object_path": str(row["object_path"]),
            "original_media_type": str(
                row["declared_media_type"]
            ),
            "byte_count": int(row["actual_bytes"]),
            "sha256": str(row["actual_sha256"]),
        }

    def _derivation_summary(
        self,
        upload_id: str,
    ) -> dict[str, object]:
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT
                    adapter_name,
                    adapter_version,
                    detected_media_type,
                    derived_byte_count,
                    derived_sha256,
                    metadata_json,
                    created_at
                FROM document_derivations
                WHERE source_upload_id = ?
                """,
                (upload_id,),
            ).fetchone()

        if row is None:
            raise NotFoundError(
                f"document derivation not found for upload: {upload_id}"
            )

        metadata = json.loads(
            str(row["metadata_json"])
        )

        return {
            "adapter_name": str(row["adapter_name"]),
            "adapter_version": str(
                row["adapter_version"]
            ),
            "detected_media_type": str(
                row["detected_media_type"]
            ),
            "derived_byte_count": int(
                row["derived_byte_count"]
            ),
            "derived_sha256": str(
                row["derived_sha256"]
            ),
            "locator_kind": str(
                metadata.get("locator_kind", "document")
            ),
            "locators_preview": list(
                metadata.get("locators_preview", [])
            ),
            "locators_count": int(
                metadata.get("locator_count", 0)
            ),
            "locators_has_more": bool(
                metadata.get("locators_has_more", False)
            ),
            "created_at": str(row["created_at"]),
        }
