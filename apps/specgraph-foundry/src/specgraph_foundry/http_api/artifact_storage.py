from __future__ import annotations

import hashlib
import posixpath
import re
from dataclasses import dataclass
from hmac import compare_digest

from ..errors import ValidationError
from .storage import (
    DownloadedObject,
    SignedDownloadTarget,
    StorageDependencyError,
    StorageObjectMissingError,
    StorageObjectTooLargeError,
    StorageProtocolError,
    SupabaseStorageClient,
)


SAFE_SEGMENT = re.compile(r"^[A-Za-z0-9._-]+$")
ARTIFACT_MEDIA_TYPES = {
    ".json": "application/json",
    ".md": "text/markdown",
    ".sha256": "text/plain",
}


class ArtifactStorageUnavailableError(RuntimeError):
    pass


class ArtifactAlreadyExistsError(RuntimeError):
    pass


class ArtifactIntegrityError(RuntimeError):
    pass


@dataclass(frozen=True)
class ArtifactStorageSettings:
    bucket: str
    max_artifact_bytes: int
    download_ttl_seconds: int

    def __post_init__(self) -> None:
        validate_segment(self.bucket)
        if self.max_artifact_bytes < 1:
            raise ValueError(
                "artifact maximum bytes must be positive"
            )
        if (
            self.download_ttl_seconds < 30
            or self.download_ttl_seconds > 900
        ):
            raise ValueError(
                "artifact download TTL must be between 30 and 900 seconds"
            )


@dataclass(frozen=True)
class StoredArtifact:
    name: str
    media_type: str
    byte_length: int
    sha256: str
    object_path: str
    data: bytes


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def validate_segment(value: str) -> str:
    normalized = value.strip()
    if (
        not normalized
        or normalized in {".", ".."}
        or "/" in normalized
        or "\\" in normalized
        or not SAFE_SEGMENT.fullmatch(normalized)
        or posixpath.normpath(normalized) != normalized
    ):
        raise ValidationError("storage path segment is invalid")
    return normalized


def validate_artifact_name(name: str) -> str:
    normalized = name.strip()
    if "/" in normalized or "\\" in normalized:
        raise ValidationError("artifact name is invalid")
    validate_segment(normalized)
    if normalized not in {
        "project.json",
        "sources.json",
        "atoms.json",
        "research.json",
        "authority_graph.json",
        "execution_graph.json",
        "traceability.json",
        "integration_bindings.json",
        "atropos_handoff.json",
        "implementation_blueprint.md",
        "manifest.json",
        "checksums.sha256",
    }:
        raise ValidationError("artifact name is not part of the export contract")
    return normalized


def media_type_for_name(name: str) -> str:
    if name.endswith(".json"):
        return ARTIFACT_MEDIA_TYPES[".json"]
    if name.endswith(".md"):
        return ARTIFACT_MEDIA_TYPES[".md"]
    if name.endswith(".sha256"):
        return ARTIFACT_MEDIA_TYPES[".sha256"]
    raise ValidationError("artifact media type is unsupported")


def artifact_object_path(
    *,
    owner_id: str,
    project_id: str,
    export_id: str,
    artifact_name: str,
) -> str:
    return "/".join(
        [
            validate_segment(owner_id),
            validate_segment(project_id),
            validate_segment(export_id),
            validate_artifact_name(artifact_name),
        ]
    )


class ArtifactStorageClient:
    def __init__(
        self,
        storage: SupabaseStorageClient,
        settings: ArtifactStorageSettings,
    ) -> None:
        self.storage = storage
        self.settings = settings

    def upload(
        self,
        *,
        authorization: str,
        artifact: StoredArtifact,
    ) -> None:
        try:
            self.storage.upload_object(
                authorization=authorization,
                bucket=self.settings.bucket,
                object_path=artifact.object_path,
                data=artifact.data,
                media_type=artifact.media_type,
            )
        except StorageProtocolError as error:
            if "already exists" in str(error):
                raise ArtifactAlreadyExistsError(
                    "artifact object already exists"
                ) from error
            raise ArtifactStorageUnavailableError(
                "artifact storage upload failed"
            ) from error
        except StorageDependencyError as error:
            raise ArtifactStorageUnavailableError(
                "artifact storage upload failed"
            ) from error

    def verified_download(
        self,
        *,
        authorization: str,
        artifact: StoredArtifact,
    ) -> DownloadedObject:
        try:
            downloaded = self.storage.download_object(
                authorization=authorization,
                bucket=self.settings.bucket,
                object_path=artifact.object_path,
                max_bytes=self.settings.max_artifact_bytes,
            )
        except (
            StorageDependencyError,
            StorageObjectMissingError,
            StorageObjectTooLargeError,
            StorageProtocolError,
        ) as error:
            raise ArtifactStorageUnavailableError(
                "artifact storage download failed"
            ) from error

        digest = sha256_bytes(downloaded.data)
        if (
            len(downloaded.data) != artifact.byte_length
            or not compare_digest(digest, artifact.sha256)
            or (
                downloaded.media_type is not None
                and downloaded.media_type.split(";", 1)[0].strip().lower()
                != artifact.media_type
            )
        ):
            raise ArtifactIntegrityError(
                "stored artifact bytes failed verification"
            )

        return downloaded

    def signed_download(
        self,
        *,
        authorization: str,
        object_path: str,
    ) -> SignedDownloadTarget:
        try:
            return self.storage.create_signed_download_target(
                authorization=authorization,
                bucket=self.settings.bucket,
                object_path=object_path,
                ttl_seconds=self.settings.download_ttl_seconds,
            )
        except (
            StorageDependencyError,
            StorageProtocolError,
        ) as error:
            raise ArtifactStorageUnavailableError(
                "artifact signed download failed"
            ) from error
