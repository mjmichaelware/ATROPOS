import hashlib
import json
from typing import Any

def canonical_serialize(value: Any) -> bytes:
    """
    Deterministically serialize python dictionaries/lists into bytes with sorted keys
    and normalized structures (excluding variable aspects like timestamps).
    """
    def sanitize(obj: Any) -> Any:
        if isinstance(obj, bytes):
            return obj.hex()
        elif isinstance(obj, dict):
            # Sort keys and filter out timestamps or run_ids if present
            cleaned = {}
            for k, v in obj.items():
                if k in {"created_at", "updated_at", "completed_at", "retrieved_at", "timestamp", "run_id", "lease_expires_at"}:
                    continue
                cleaned[k] = sanitize(v)
            return cleaned
        elif isinstance(obj, list):
            # Recursively sanitize items
            return [sanitize(item) for item in obj]
        elif isinstance(obj, tuple):
            return [sanitize(item) for item in obj]
        return obj

    sanitized = sanitize(value)
    return json.dumps(
        sanitized,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False
    ).encode("utf-8")

def generate_fingerprint(value: Any) -> str:
    """
    Generate SHA-256 fingerprint hash of a canonicalized representation of the value.
    """
    serialized = canonical_serialize(value)
    return hashlib.sha256(serialized).hexdigest()
