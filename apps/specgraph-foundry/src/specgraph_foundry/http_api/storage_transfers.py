"""Moving bytes to and from object storage.

The two operations that carry a payload, and the only ones bounded by a size
limit -- an oversized object must be refused before it is buffered, not after.
"""

from __future__ import annotations

from .storage_models import DownloadedObject
from .storage_models import MAX_JSON_RESPONSE_BYTES
from .storage_models import StorageDependencyError
from .storage_models import StorageObjectMissingError
from .storage_models import StorageObjectTooLargeError
from .storage_models import StoragePermanentError
from .storage_models import StorageProtocolError
from .storage_models import *  # noqa: F401,F403
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen
from .storage_transport import headers
from .storage_transport import is_disguised_not_found
from .storage_transport import validate_response_location
import json


def download_object(
    client,
    *,
    authorization: str,
    bucket: str,
    object_path: str,
    max_bytes: int,
) -> DownloadedObject:
    # RLS-authorized downloads use the plain "/object/{bucket}/{path}"
    # route with the caller's JWT in the Authorization header - matching
    # the official storage-js SDK's `.download()` implementation. There
    # is no "/authenticated/" path segment; authorization comes entirely
    # from the header, and adding that segment produces a 400 from
    # Supabase's storage-api instead of the object bytes.
    request = Request(
        client.origin
        + "/storage/v1/object/"
        + quote(bucket, safe="")
        + "/"
        + quote(object_path, safe="/"),
        method="GET",
        headers=headers(client, authorization),
    )

    try:
        with client.opener(
            request,
            timeout=client.timeout_seconds,
        ) as response:
            validate_response_location(client, 
                response.geturl()
            )
            payload = response.read(max_bytes + 1)
            media_type = response.headers.get(
                "content-type"
            )
    except HTTPError as error:
        if error.code == 404 or is_disguised_not_found(error):
            raise StorageObjectMissingError(
                "storage object not found"
            ) from error

        raise StorageDependencyError(
            "storage download failed"
        ) from error
    except URLError as error:
        raise StorageDependencyError(
            "storage download failed"
        ) from error

    if len(payload) > max_bytes:
        raise StorageObjectTooLargeError(
            "storage object exceeds the configured limit"
        )

    return DownloadedObject(
        data=payload,
        media_type=media_type,
    )


def upload_object(
    client,
    *,
    authorization: str,
    bucket: str,
    object_path: str,
    data: bytes,
    media_type: str,
) -> None:
    request = Request(
        client.origin
        + "/storage/v1/object/"
        + quote(bucket, safe="")
        + "/"
        + quote(object_path, safe="/"),
        method="POST",
        data=data,
        headers={
            **headers(client, authorization),
            "Content-Type": media_type,
            "x-upsert": "false",
        },
    )

    try:
        with client.opener(
            request,
            timeout=client.timeout_seconds,
        ) as response:
            validate_response_location(client, 
                response.geturl()
            )
            response.read(
                MAX_JSON_RESPONSE_BYTES
            )
    except HTTPError as error:
        if error.code == 409:
            raise StorageProtocolError(
                "storage object already exists"
            ) from error
        if 400 <= error.code < 500:
            detail = ""
            try:
                body = json.loads(
                    error.read(MAX_JSON_RESPONSE_BYTES).decode("utf-8")
                )
                detail = (
                    body.get("message")
                    or body.get("error")
                    or ""
                )
            except Exception:
                pass
            raise StoragePermanentError(
                f"storage upload rejected (HTTP {error.code})"
                + (f": {detail}" if detail else "")
            ) from error
        raise StorageDependencyError(
            "storage upload failed"
        ) from error
    except URLError as error:
        raise StorageDependencyError(
            "storage upload failed"
        ) from error
