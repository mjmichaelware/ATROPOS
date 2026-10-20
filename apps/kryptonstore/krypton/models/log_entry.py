"""Core models: LogEntry."""
from dataclasses import dataclass
import time

@dataclass
class LogEntry:
    index: int
    term: int
    command: str
    key: str
    value: bytes
    timestamp: float = 0.0

    def __post_init__(self) -> None:
        if self.timestamp == 0.0:
            self.timestamp = time.time()
