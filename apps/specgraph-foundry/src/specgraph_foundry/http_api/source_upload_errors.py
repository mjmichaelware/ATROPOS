"""The ways a source upload can fail, and the settings that bound it.

Nine typed failures rather than one. Each maps to a different HTTP status and a
different thing the caller should do -- an expired intent is retryable, an
integrity mismatch is not, an encrypted document will never succeed -- and
collapsing them would make all three look the same to a client.
"""

from __future__ import annotations

from dataclasses import dataclass

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
