from __future__ import annotations

from .storage_models import DownloadedObject
from .storage_models import SignedDownloadTarget
from .storage_models import SignedUploadTarget
from .storage_models import *  # noqa: F401,F403 - re-exported

from .storage_transfers import download_object, upload_object
from .storage_signing import bearer_token, create_signed_download_target, create_signed_upload_target, validated_signed_url

import json
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urljoin, urlsplit
from urllib.request import Request, urlopen






















class SupabaseStorageClient:
    def __init__(
        self,
        supabase_url: str,
        anon_key: str,
        *,
        timeout_seconds: float,
        opener: Any = urlopen,
    ) -> None:
        self.supabase_url = supabase_url.strip().rstrip("/")
        self.anon_key = anon_key.strip()
        self.timeout_seconds = timeout_seconds
        self.opener = opener
        origin = urlsplit(self.supabase_url)

        if origin.scheme not in {"http", "https"}:
            raise ValueError("SUPABASE_URL must use http or https")

        if not origin.netloc:
            raise ValueError("SUPABASE_URL is required")

        if not self.anon_key:
            raise ValueError("SUPABASE_ANON_KEY is required")

        if timeout_seconds <= 0:
            raise ValueError("storage timeout must be positive")

        self.origin = f"{origin.scheme}://{origin.netloc}"

    @staticmethod
    def bearer_token(
        authorization: str | None,
    ) -> str:
        """Delegates to :func:`storage_signing.bearer_token`."""
        return bearer_token(
            authorization,
        )


    def create_signed_upload_target(
        self,
        *,
        authorization: str,
        bucket: str,
        object_path: str,
        ttl_seconds: int,
    ) -> SignedUploadTarget:
        """Delegates to :func:`storage_signing.create_signed_upload_target`."""
        return create_signed_upload_target(
            self,
            authorization=authorization,
            bucket=bucket,
            object_path=object_path,
            ttl_seconds=ttl_seconds,
        )


    def download_object(
        self,
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
        """Delegates to :func:`storage_transfers.download_object`."""
        return download_object(
            self,
            authorization=authorization,
            bucket=bucket,
            object_path=object_path,
            max_bytes=max_bytes,
        )


    def upload_object(
        self,
        *,
        authorization: str,
        bucket: str,
        object_path: str,
        data: bytes,
        media_type: str,
    ) -> None:
        """Delegates to :func:`storage_transfers.upload_object`."""
        return upload_object(
            self,
            authorization=authorization,
            bucket=bucket,
            object_path=object_path,
            data=data,
            media_type=media_type,
        )


    def create_signed_download_target(
        self,
        *,
        authorization: str,
        bucket: str,
        object_path: str,
        ttl_seconds: int,
        force_download: bool = False,
    ) -> SignedDownloadTarget:
        """Delegates to :func:`storage_signing.create_signed_download_target`."""
        return create_signed_download_target(
            self,
            authorization=authorization,
            bucket=bucket,
            object_path=object_path,
            ttl_seconds=ttl_seconds,
            force_download=force_download,
        )



    def _validated_signed_url(
        self,
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
        """Delegates to :func:`storage_signing.validated_signed_url`."""
        return validated_signed_url(
            self,
            url_value,
        )




