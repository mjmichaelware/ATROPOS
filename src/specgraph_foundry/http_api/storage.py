from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urljoin, urlsplit
from urllib.request import Request, urlopen


MAX_JSON_RESPONSE_BYTES = 64 * 1024


class StorageProtocolError(RuntimeError):
    pass


class StorageDependencyError(RuntimeError):
    pass


class StoragePermanentError(RuntimeError):
    """Raised when storage rejects an upload with a 4xx response.

    4xx responses (auth failures, RLS violations, bad requests) indicate
    a configuration or protocol problem that will not resolve on retry.
    """
    pass


class StorageObjectMissingError(RuntimeError):
    pass


class StorageObjectTooLargeError(RuntimeError):
    pass


@dataclass(frozen=True)
class SignedUploadTarget:
    url: str
    expires_at: str
    required_headers: dict[str, str]


@dataclass(frozen=True)
class SignedDownloadTarget:
    url: str
    expires_at: str


@dataclass(frozen=True)
class DownloadedObject:
    data: bytes
    media_type: str | None


def _utc_now() -> datetime:
    return datetime.now(UTC)


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

    def create_signed_upload_target(
        self,
        *,
        authorization: str,
        bucket: str,
        object_path: str,
        ttl_seconds: int,
    ) -> SignedUploadTarget:
        body = self._json_request(
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

        signed_url = self._validated_signed_url(
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
        request = Request(
            self.origin
            + "/storage/v1/object/"
            + quote(bucket, safe="")
            + "/"
            + quote(object_path, safe="/"),
            method="GET",
            headers=self._headers(authorization),
        )

        try:
            with self.opener(
                request,
                timeout=self.timeout_seconds,
            ) as response:
                self._validate_response_location(
                    response.geturl()
                )
                payload = response.read(max_bytes + 1)
                media_type = response.headers.get(
                    "content-type"
                )
        except HTTPError as error:
            if error.code == 404 or self._is_disguised_not_found(error):
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
        self,
        *,
        authorization: str,
        bucket: str,
        object_path: str,
        data: bytes,
        media_type: str,
    ) -> None:
        request = Request(
            self.origin
            + "/storage/v1/object/"
            + quote(bucket, safe="")
            + "/"
            + quote(object_path, safe="/"),
            method="POST",
            data=data,
            headers={
                **self._headers(authorization),
                "Content-Type": media_type,
                "x-upsert": "false",
            },
        )

        try:
            with self.opener(
                request,
                timeout=self.timeout_seconds,
            ) as response:
                self._validate_response_location(
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

    def create_signed_download_target(
        self,
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

        body = self._json_request(
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

        signed_url = self._validated_signed_url(
            url_value.strip()
        )

        return SignedDownloadTarget(
            url=signed_url,
            expires_at=(
                _utc_now()
                + timedelta(seconds=ttl_seconds)
            ).isoformat(),
        )

    def _json_request(
        self,
        *,
        method: str,
        path: str,
        authorization: str,
        payload: dict[str, object],
    ) -> dict[str, object]:
        encoded = json.dumps(
            payload,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")

        request = Request(
            self.origin + path,
            method=method,
            data=encoded,
            headers={
                **self._headers(authorization),
                "Accept": "application/json",
                "Content-Type": "application/json",
            },
        )

        try:
            with self.opener(
                request,
                timeout=self.timeout_seconds,
            ) as response:
                self._validate_response_location(
                    response.geturl()
                )
                raw = response.read(
                    MAX_JSON_RESPONSE_BYTES + 1
                )
        except HTTPError as error:
            raise StorageDependencyError(
                "storage request failed"
            ) from error
        except URLError as error:
            raise StorageDependencyError(
                "storage request failed"
            ) from error

        if len(raw) > MAX_JSON_RESPONSE_BYTES:
            raise StorageProtocolError(
                "storage response exceeds the limit"
            )

        try:
            decoded = json.loads(
                raw.decode("utf-8")
            )
        except (
            UnicodeDecodeError,
            json.JSONDecodeError,
        ) as error:
            raise StorageProtocolError(
                "storage response is invalid"
            ) from error

        if not isinstance(decoded, dict):
            raise StorageProtocolError(
                "storage response is invalid"
            )

        return decoded

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
        if url_value.startswith(("http://", "https://")):
            resolved = url_value
        elif url_value.startswith("/"):
            resolved = self.origin + "/storage/v1" + url_value
        else:
            resolved = urljoin(
                self.origin + "/storage/v1/",
                url_value,
            )
        parsed = urlsplit(resolved)

        if (
            f"{parsed.scheme}://{parsed.netloc}"
            != self.origin
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

    @staticmethod
    def _is_disguised_not_found(
        error: HTTPError,
    ) -> bool:
        # Supabase Storage reports a missing S3 object as HTTP 400 (not
        # 404) with a JSON body carrying the real status separately, e.g.
        # {"statusCode": "404", "error": "Not found", "message": "..."}.
        # Without this check that disguised 404 is indistinguishable from
        # a genuine dependency failure and gets misreported as one.
        if error.code != 400:
            return False
        try:
            body = json.loads(error.read().decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError, OSError, ValueError):
            return False
        return (
            isinstance(body, dict)
            and str(body.get("statusCode")) == "404"
        )

    def _validate_response_location(
        self,
        response_url: str,
    ) -> None:
        parsed = urlsplit(response_url)

        if (
            f"{parsed.scheme}://{parsed.netloc}"
            != self.origin
        ):
            raise StorageProtocolError(
                "storage redirected to an invalid origin"
            )

    def _headers(
        self,
        authorization: str,
    ) -> dict[str, str]:
        token = self.bearer_token(authorization)
        return {
            "Authorization": f"Bearer {token}",
            "apikey": self.anon_key,
        }
