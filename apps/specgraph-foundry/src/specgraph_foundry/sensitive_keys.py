"""Recognising configuration keys that must never be exported.

One list and one predicate, alone in a module on purpose. This is the check that
decides whether a secret leaves the system, so it should be findable by name,
reviewable on its own, and changed deliberately rather than while editing an
export path.
"""

from __future__ import annotations


SENSITIVE_KEY_FRAGMENTS = (
    "api_key",
    "apikey",
    "access_key",
    "secret",
    "password",
    "passwd",
    "token",
    "credential",
    "private_key",
    "client_secret",
)


def contains_sensitive_key(
    value: object,
) -> bool:
    if isinstance(value, dict):
        for key, nested in value.items():
            normalized = str(key).casefold()

            if any(
                fragment in normalized
                for fragment
                in SENSITIVE_KEY_FRAGMENTS
            ):
                return True

            if contains_sensitive_key(nested):
                return True

    elif isinstance(value, list):
        return any(
            contains_sensitive_key(item)
            for item in value
        )

    return False
