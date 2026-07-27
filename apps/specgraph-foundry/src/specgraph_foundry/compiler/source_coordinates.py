import hashlib
from typing import NamedTuple

class SourceCoordinates(NamedTuple):
    byte_start: int
    byte_end: int
    line_start: int
    line_end: int
    char_start: int | None = None
    char_end: int | None = None

    def to_dict(self) -> dict[str, object]:
        return {
            "byte_start": self.byte_start,
            "byte_end": self.byte_end,
            "line_start": self.line_start,
            "line_end": self.line_end,
            "char_start": self.char_start,
            "char_end": self.char_end,
        }

def compute_sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()
