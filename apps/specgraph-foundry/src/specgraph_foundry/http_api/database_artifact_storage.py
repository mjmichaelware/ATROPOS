from __future__ import annotations

import base64
import hashlib
import hmac
import json
import sqlite3
import time
import uuid
from datetime import datetime, timezone

from ..database import Database
from .artifact_storage import (
    ArtifactAlreadyExistsError,
    ArtifactIntegrityError,
    ArtifactStorageSettings,
    ArtifactStorageUnavailableError,
    StoredArtifact,
    sha256_bytes,
)
from .storage import (
    DownloadedObject,
    SignedDownloadTarget,
)

_SEP = "."


def _key_bytes(signing_key: str) -> bytes:
    return signing_key.encode("utf-8") or b"dev"


def _b64enc(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def _b64dec(s: str) -> bytes:
    pad = (-len(s)) % 4
    return base64.urlsafe_b64decode(s + "=" * pad)


def _sign(payload_b64: str, key: bytes) -> str:
    sig = hmac.new(key, payload_b64.encode(), hashlib.sha256).digest()
    return _b64enc(sig)


def create_artifact_token(
    object_path: str,
    *,
    ttl_seconds: int,
    signing_key: str,
) -> str:
    payload = json.dumps(
        {"op": object_path, "exp": int(time.time()) + ttl_seconds},
        separators=(",", ":"),
    )
    payload_b64 = _b64enc(payload.encode())
    return f"{payload_b64}{_SEP}{_sign(payload_b64, _key_bytes(signing_key))}"


def verify_artifact_token(token: str, signing_key: str) -> str | None:
    """Return object_path if token is valid and unexpired, else None."""
    try:
        payload_b64, sig_b64 = token.split(_SEP, 1)
    except ValueError:
        return None
    key = _key_bytes(signing_key)
    if not hmac.compare_digest(_sign(payload_b64, key), sig_b64):
        return None
    try:
        payload = json.loads(_b64dec(payload_b64).decode())
        if int(payload["exp"]) < int(time.time()):
            return None
        return str(payload["op"])
    except (ValueError, KeyError, TypeError):
        return None


class DatabaseArtifactStorageClient:
    """Stores artifact blobs in the application database instead of object storage."""

    def __init__(
        self,
        database: Database,
        settings: ArtifactStorageSettings,
        api_base_url: str,
        signing_key: str,
    ) -> None:
        self.database = database
        self.settings = settings
        self._api_base_url = api_base_url.rstrip("/")
        self._signing_key = signing_key

    def upload(
        self,
        *,
        authorization: str,
        artifact: StoredArtifact,
    ) -> None:
        try:
            with self.database.connect() as connection:
                connection.execute(
                    """
                    INSERT INTO artifact_blobs
                        (id, object_path, media_type, data, created_at)
                    VALUES
                        (?, ?, ?, ?, ?)
                    """,
                    (
                        str(uuid.uuid4()),
                        artifact.object_path,
                        artifact.media_type,
                        artifact.data,
                        datetime.now(timezone.utc).isoformat(),
                    ),
                )
        except sqlite3.IntegrityError as error:
            msg = str(error).lower()
            if "unique" in msg or "duplicate" in msg:
                raise ArtifactAlreadyExistsError(
                    "artifact blob already exists"
                ) from error
            raise ArtifactStorageUnavailableError(
                "artifact blob insert failed"
            ) from error
        except Exception as error:
            raise ArtifactStorageUnavailableError(
                "artifact blob insert failed"
            ) from error

    def verified_download(
        self,
        *,
        authorization: str,
        artifact: StoredArtifact,
    ) -> DownloadedObject:
        try:
            with self.database.connect() as connection:
                row = connection.execute(
                    """
                    SELECT data, media_type
                    FROM artifact_blobs
                    WHERE object_path = ?
                    """,
                    (artifact.object_path,),
                ).fetchone()
        except Exception as error:
            raise ArtifactStorageUnavailableError(
                "artifact blob fetch failed"
            ) from error

        if row is None:
            raise ArtifactStorageUnavailableError(
                "artifact blob not found"
            )

        raw = row["data"]
        data = bytes(raw) if isinstance(raw, (memoryview, bytearray)) else raw
        media_type = str(row["media_type"])

        digest = sha256_bytes(data)
        if len(data) != artifact.byte_length or not hmac.compare_digest(
            digest, artifact.sha256
        ):
            raise ArtifactIntegrityError("stored artifact bytes failed verification")

        return DownloadedObject(data=data, media_type=media_type)

    def signed_download(
        self,
        *,
        authorization: str,
        object_path: str,
    ) -> SignedDownloadTarget:
        token = create_artifact_token(
            object_path,
            ttl_seconds=self.settings.download_ttl_seconds,
            signing_key=self._signing_key,
        )
        expires_at = datetime.fromtimestamp(
            time.time() + self.settings.download_ttl_seconds,
            tz=timezone.utc,
        ).isoformat()
        return SignedDownloadTarget(
            url=f"{self._api_base_url}/v1/artifact-downloads/{token}",
            expires_at=expires_at,
        )
