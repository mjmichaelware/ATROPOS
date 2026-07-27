import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class Settings:
    database_path: Path
    host: str
    port: int
    database_url: str | None = None
    database_owner_id: str | None = None

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(
            database_path=Path(
                os.environ.get(
                    "SPECGRAPH_DATABASE_PATH",
                    ".specgraph/specgraph.sqlite3",
                )
            ),
            host=os.environ.get(
                "SPECGRAPH_HOST",
                "127.0.0.1",
            ),
            port=int(
                os.environ.get(
                    "SPECGRAPH_PORT",
                    "8787",
                )
            ),
            database_url=(
                os.environ.get(
                    "SPECGRAPH_DATABASE_URL"
                )
                or None
            ),
            database_owner_id=(
                os.environ.get(
                    "SPECGRAPH_OWNER_ID"
                )
                or None
            ),
        )
