"""Signed URLs and the token that authorises them.

Every URL handed to a client comes from here, so URL validation and the bearer
token belong together: a signed URL that was not validated is a redirect an
attacker chose.
"""

from __future__ import annotations

from .storage_models import _utc_now
from .storage_models import SignedDownloadTarget
from .storage_models import SignedUploadTarget
from .storage_models import StorageDependencyError
from .storage_models import StorageProtocolError
from .storage_models import *  # noqa: F401,F403
from urllib.parse import quote, urljoin, urlsplit
from .storage_transport import json_request
from datetime import timedelta
import json


def create_signed_upload_target(
    client,
    *,
    authorization: str,
    bucket: str,
    object_path: str,
    ttl_seconds: int,
) -> SignedUploadTarget:
    body = json_request(client, 
        method="POST",
        path=(
            "/storage/v1/object/upload/sign/"
            f"{quote(bucket, safe='')}/"
            f"{quote(object_path, safe='/')}"
        ),
        authorization=authorization,
        payload={"expiresIn": ttl_seconds},
    )

    url_value = body.get("url")

    if not isinstance(url_value, str) or not url_value.strip():
        raise StorageProtocolError(
            "storage signing response is invalid"
        )

    signed_url = validated_signed_url(client, 
        url_value.strip()
    )

    return SignedUploadTarget(
        url=signed_url,
        expires_at=(
            _utc_now()
            + timedelta(seconds=ttl_seconds)
        ).isoformat(),
        required_headers={},
    )


def create_signed_download_target(
    client,
    *,
    authorization: str,
    bucket: str,
    object_path: str,
    ttl_seconds: int,
    force_download: bool = False,
) -> SignedDownloadTarget:
    payload: dict[str, object] = {"expiresIn": ttl_seconds}
    if force_download:
        # Without this, Supabase Storage serves the object with no
        # Content-Disposition, so browsers open text/PDF artifacts
        # inline (a new tab, or the OS PDF viewer) instead of saving
        # them - on mobile that's an extra manual "save" step, not the
        # direct download to device storage this is meant to be.
        payload["download"] = True

    body = json_request(client, 
        method="POST",
        path=(
            "/storage/v1/object/sign/"
            f"{quote(bucket, safe='')}/"
            f"{quote(object_path, safe='/')}"
        ),
        authorization=authorization,
        payload=payload,
    )

    url_value = (
        body.get("signedURL")
        or body.get("signedUrl")
        or body.get("url")
    )

    if not isinstance(url_value, str) or not url_value.strip():
        raise StorageProtocolError(
            "storage signing response is invalid"
        )

    signed_url = validated_signed_url(client, 
        url_value.strip()
    )

    return SignedDownloadTarget(
        url=signed_url,
        expires_at=(
            _utc_now()
            + timedelta(seconds=ttl_seconds)
        ).isoformat(),
    )


def validated_signed_url(
    client,
    url_value: str,
) -> str:
    # Supabase Storage's signing endpoints return a path relative to
    # the storage API root (e.g. "/object/upload/sign/bucket/path?token=..."),
    # not relative to the bare origin - the official SDKs build the
    # final URL via straight concatenation of `${storageApiUrl}${data.url}`
    # where storageApiUrl already includes "/storage/v1". urljoin()
    # can't be used here: given an absolute-path reference (one
    # starting with "/"), it replaces the base's entire path,
    # discarding "/storage/v1" regardless of what base is passed in.
    # Concatenation matching the official SDKs is the only correct
    # way to reconstruct this specific URL shape.
    if url_value.startswith(("http://", "https://")):
        resolved = url_value
    elif url_value.startswith("/"):
        resolved = client.origin + "/storage/v1" + url_value
    else:
        resolved = urljoin(
            client.origin + "/storage/v1/",
            url_value,
        )
    parsed = urlsplit(resolved)

    if (
        f"{parsed.scheme}://{parsed.netloc}"
        != client.origin
    ):
        raise StorageProtocolError(
            "signed upload URL origin is invalid"
        )

    if not parsed.path.startswith(
        "/storage/v1/"
    ):
        raise StorageProtocolError(
            "signed upload URL path is invalid"
        )

    return resolved


def bearer_token(
    authorization: str | None,
) -> str:
    if authorization is None:
        raise StorageDependencyError(
            "authorization is required"
        )

    scheme, separator, token = authorization.partition(" ")
    normalized = token.strip()

    if (
        not separator
        or scheme.casefold() != "bearer"
        or not normalized
        or " " in normalized
    ):
        raise StorageDependencyError(
            "authorization is invalid"
        )

    return normalized
