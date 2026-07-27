import os
from pathlib import Path


SECRET_NAMES = (
    "SUPABASE_URL",
    "SUPABASE_ANON_KEY",
    "SUPABASE_SERVICE_ROLE_KEY",
    "GOOGLE_CLOUD_PROJECT",
    "GOOGLE_APPLICATION_CREDENTIALS",
    "GOOGLE_OAUTH_CLIENT_ID",
    "GOOGLE_OAUTH_CLIENT_SECRET",
    "GH_TOKEN",
    "GITHUB_TOKEN",
)


def inspect() -> dict[str, object]:
    candidate_files = (
        Path.cwd() / ".env",
        Path.home() / ".env",
        Path.home() / "ATROPOS" / ".env",
        Path.home() / ".config" / "atropos" / ".env",
    )

    file_secret_names: set[str] = set()
    checked_files: list[str] = []

    for path in candidate_files:
        if not path.is_file():
            continue

        checked_files.append(str(path))

        for raw_line in path.read_text(
            encoding="utf-8",
            errors="ignore",
        ).splitlines():
            line = raw_line.strip()

            if (
                not line
                or line.startswith("#")
                or "=" not in line
            ):
                continue

            name = (
                line.split("=", 1)[0]
                .strip()
                .removeprefix("export ")
                .strip()
            )

            if name:
                file_secret_names.add(name)

    return {
        "values_exposed": False,
        "env_files_checked": checked_files,
        "secrets": {
            name: {
                "environment": bool(
                    os.environ.get(name)
                ),
                "env_file": (
                    name in file_secret_names
                ),
            }
            for name in SECRET_NAMES
        },
    }
