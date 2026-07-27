from dataclasses import dataclass, field
from typing import Mapping


@dataclass(frozen=True)
class Principal:
    user_id: str
    email: str | None = None
    role: str = "authenticated"
    claims: Mapping[str, object] = field(
        default_factory=dict
    )

    def public(self) -> dict[str, object]:
        return {
            "id": self.user_id,
            "email": self.email,
            "role": self.role,
        }


@dataclass(frozen=True)
class ApiRequest:
    method: str
    raw_path: str
    headers: Mapping[str, str]
    payload: dict[str, object]
    request_id: str

    @property
    def authorization(self) -> str | None:
        value = self.headers.get(
            "authorization"
        )

        if value is None:
            return None

        normalized = value.strip()

        return normalized or None

    @property
    def idempotency_key(self) -> str | None:
        value = self.headers.get(
            "idempotency-key"
        )

        if value is None:
            return None

        normalized = value.strip()
        return normalized or None

    @property
    def if_match(self) -> str | None:
        value = self.headers.get(
            "if-match"
        )

        if value is None:
            return None

        normalized = value.strip()
        return normalized or None


@dataclass(frozen=True)
class ApiResponse:
    status: int
    body: dict[str, object]
    headers: Mapping[str, str] = field(
        default_factory=dict
    )
