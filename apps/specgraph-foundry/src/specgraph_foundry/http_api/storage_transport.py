"""The HTTP transport under the storage client.

Request building, header assembly, and the two checks that stop a redirect or a
disguised 404 from being read as success.
"""

from __future__ import annotations

from .storage_models import MAX_JSON_RESPONSE_BYTES
from .storage_models import StorageDependencyError
from .storage_models import StorageProtocolError
from .storage_models import *  # noqa: F401,F403
import json
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urljoin, urlsplit
from urllib.request import Request, urlopen


def json_request(
    client,
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
        client.origin + path,
        method=method,
        data=encoded,
        headers={
            **headers(client, authorization),
            "Accept": "application/json",
            "Content-Type": "application/json",
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


def is_disguised_not_found(
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


def validate_response_location(
    client,
    response_url: str,
) -> None:
    parsed = urlsplit(response_url)

    if (
        f"{parsed.scheme}://{parsed.netloc}"
        != client.origin
    ):
        raise StorageProtocolError(
            "storage redirected to an invalid origin"
        )


def headers(
    client,
    authorization: str,
) -> dict[str, str]:
    token = client.bearer_token(authorization)
    return {
        "Authorization": f"Bearer {token}",
        "apikey": client.anon_key,
    }
