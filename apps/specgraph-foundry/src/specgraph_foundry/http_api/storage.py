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
        request = Request(
            self.origin
            + "/storage/v1/object/authenticated/"
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
            if error.code == 404:
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
    ) -> SignedDownloadTarget:
        body = self._json_request(
            method="POST",
            path=(
                "/storage/v1/object/sign/"
                f"{quote(bucket, safe='')}/"
                f"{quote(object_path, safe='/')}"
            ),
            authorization=authorization,
            payload={"expiresIn": ttl_seconds},
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
        resolved = urljoin(
            self.origin + "/",
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
