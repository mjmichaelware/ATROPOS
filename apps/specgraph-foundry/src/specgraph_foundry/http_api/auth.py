import json
import uuid
from collections.abc import Callable
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from .models import Principal


class AuthenticationError(RuntimeError):
    def __init__(
        self,
        message: str,
        *,
        status: int = 401,
        code: str = "UNAUTHENTICATED",
    ) -> None:
        super().__init__(message)
        self.status = status
        self.code = code


class SupabaseAuthClient:
    def __init__(
        self,
        supabase_url: str,
        anon_key: str,
        *,
        timeout_seconds: float = 10.0,
        opener: Callable[..., Any] = urlopen,
    ) -> None:
        self.supabase_url = (
            supabase_url.strip().rstrip("/")
        )
        self.anon_key = anon_key.strip()
        self.timeout_seconds = timeout_seconds
        self.opener = opener

        if not self.supabase_url:
            raise ValueError(
                "SUPABASE_URL is required"
            )

        if not self.anon_key:
            raise ValueError(
                "SUPABASE_ANON_KEY is required"
            )

        if timeout_seconds <= 0:
            raise ValueError(
                "authentication timeout must be positive"
            )

    def authenticate(
        self,
        authorization: str | None,
    ) -> Principal:
        token = self._bearer_token(
            authorization
        )

        request = Request(
            self.supabase_url
            + "/auth/v1/user",
            method="GET",
            headers={
                "Accept": "application/json",
                "Authorization": (
                    f"Bearer {token}"
                ),
                "apikey": self.anon_key,
            },
        )

        try:
            with self.opener(
                request,
                timeout=self.timeout_seconds,
            ) as response:
                payload = response.read()

        except HTTPError as error:
            if error.code in {
                400,
                401,
                403,
            }:
                raise AuthenticationError(
                    "access token is invalid or expired"
                ) from error

            raise AuthenticationError(
                "Supabase authentication service "
                "rejected the request",
                status=503,
                code="AUTH_SERVICE_UNAVAILABLE",
            ) from error

        except URLError as error:
            raise AuthenticationError(
                "Supabase authentication service "
                "is unavailable",
                status=503,
                code="AUTH_SERVICE_UNAVAILABLE",
            ) from error

        try:
            decoded = json.loads(
                payload.decode("utf-8")
            )
        except (
            UnicodeDecodeError,
            json.JSONDecodeError,
        ) as error:
            raise AuthenticationError(
                "Supabase authentication returned "
                "an invalid response",
                status=503,
                code="AUTH_SERVICE_INVALID_RESPONSE",
            ) from error

        if not isinstance(decoded, dict):
            raise AuthenticationError(
                "Supabase authentication returned "
                "an invalid user",
                status=503,
                code="AUTH_SERVICE_INVALID_RESPONSE",
            )

        user_id = str(
            decoded.get("id", "")
        ).strip()

        try:
            uuid.UUID(user_id)
        except ValueError as error:
            raise AuthenticationError(
                "authenticated user ID is not "
                "a valid UUID",
                status=503,
                code="AUTH_SERVICE_INVALID_RESPONSE",
            ) from error

        email_value = decoded.get("email")

        email = (
            str(email_value).strip()
            if email_value
            else None
        )

        role = str(
            decoded.get(
                "role",
                "authenticated",
            )
        ).strip()

        if role != "authenticated":
            raise AuthenticationError(
                "authenticated role is required",
                status=403,
                code="FORBIDDEN",
            )

        return Principal(
            user_id=user_id,
            email=email,
            role=role,
            claims=decoded,
        )

    @staticmethod
    def _bearer_token(
        authorization: str | None,
    ) -> str:
        if authorization is None:
            raise AuthenticationError(
                "Authorization bearer token "
                "is required"
            )

        scheme, separator, token = (
            authorization.partition(" ")
        )

        token = token.strip()

        if (
            not separator
            or scheme.casefold() != "bearer"
            or not token
            or " " in token
        ):
            raise AuthenticationError(
                "Authorization must use "
                "Bearer ACCESS_TOKEN"
            )

        if len(token) > 16384:
            raise AuthenticationError(
                "access token is too large"
            )

        return token
