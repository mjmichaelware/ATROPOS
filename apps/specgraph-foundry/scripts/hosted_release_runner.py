import getpass
import json
import os
import secrets
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

import psycopg

from hosted_release_audit import (
    run_audit,
)


ROOT = Path(__file__).resolve().parents[1]


def project_ref() -> str:
    configured = os.environ.get(
        "SPECGRAPH_PROJECT_REF",
        "",
    ).strip()

    if configured:
        return configured

    reference_file = (
        ROOT
        / "supabase/.temp/project-ref"
    )

    if reference_file.is_file():
        value = reference_file.read_text(
            encoding="utf-8"
        ).strip()

        if value:
            return value

    return input(
        "Supabase project reference ID: "
    ).strip()


def command_api_keys(
    reference: str,
) -> object | None:
    commands = [
        [
            "supabase",
            "projects",
            "api-keys",
            "--project-ref",
            reference,
            "--output",
            "json",
        ],
        [
            "supabase",
            "projects",
            "api-keys",
            "--project-ref",
            reference,
            "-o",
            "json",
        ],
    ]

    for command in commands:
        result = subprocess.run(
            command,
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        if result.returncode != 0:
            continue

        try:
            return json.loads(
                result.stdout
            )
        except json.JSONDecodeError:
            continue

    return None


def find_service_key(
    value: object,
) -> str | None:
    if isinstance(value, list):
        for item in value:
            found = find_service_key(
                item
            )

            if found:
                return found

        return None

    if not isinstance(value, dict):
        return None

    identity = " ".join(
        str(value.get(key, ""))
        for key in (
            "name",
            "id",
            "type",
            "role",
            "key_name",
            "description",
        )
    ).casefold()

    if (
        "service_role" in identity
        or "service role" in identity
    ):
        for key in (
            "api_key",
            "key",
            "value",
            "secret",
        ):
            candidate = value.get(key)

            if (
                isinstance(candidate, str)
                and candidate.strip()
            ):
                return candidate.strip()

    for nested in value.values():
        found = find_service_key(
            nested
        )

        if found:
            return found

    return None


def request_json(
    request: urllib.request.Request,
) -> object:
    try:
        with urllib.request.urlopen(
            request,
            timeout=30,
        ) as response:
            return json.loads(
                response.read().decode(
                    "utf-8"
                )
            )
    except urllib.error.HTTPError as error:
        body = error.read().decode(
            "utf-8",
            errors="replace",
        )

        raise RuntimeError(
            (
                "Supabase Auth request failed "
                f"with HTTP {error.code}: "
                f"{body}"
            )
        ) from error


def create_audit_user(
    reference: str,
    service_key: str,
) -> tuple[str, str, str]:
    email = (
        "specgraph-audit-"
        + uuid.uuid4().hex
        + "@example.com"
    )

    password = (
        secrets.token_urlsafe(32)
    )

    payload = json.dumps(
        {
            "email": email,
            "password": password,
            "email_confirm": True,
            "user_metadata": {
                "purpose": (
                    "specgraph-hosted-"
                    "release-audit"
                )
            },
        }
    ).encode("utf-8")

    request = urllib.request.Request(
        (
            f"https://{reference}."
            "supabase.co/auth/v1/admin/users"
        ),
        data=payload,
        method="POST",
        headers={
            "apikey": service_key,
            "Authorization": (
                f"Bearer {service_key}"
            ),
            "Content-Type": (
                "application/json"
            ),
        },
    )

    response = request_json(request)

    if not isinstance(response, dict):
        raise RuntimeError(
            "Unexpected Auth response"
        )

    owner_id = response.get("id")

    if not owner_id:
        user = response.get("user")

        if isinstance(user, dict):
            owner_id = user.get("id")

    if not isinstance(owner_id, str):
        raise RuntimeError(
            "Auth response did not contain "
            "a user ID"
        )

    return owner_id, email, password


def delete_audit_user(
    reference: str,
    service_key: str,
    owner_id: str,
) -> None:
    request = urllib.request.Request(
        (
            f"https://{reference}."
            "supabase.co/auth/v1/admin/users/"
            f"{owner_id}"
        ),
        method="DELETE",
        headers={
            "apikey": service_key,
            "Authorization": (
                f"Bearer {service_key}"
            ),
        },
    )

    try:
        request_json(request)
    except Exception as error:
        print(
            (
                "WARNING: temporary Auth "
                f"user cleanup failed: {error}"
            ),
            file=sys.stderr,
        )


def test_database_url(
    value: str,
) -> bool:
    try:
        with psycopg.connect(
            value,
            connect_timeout=12,
            prepare_threshold=None,
        ) as connection:
            connection.execute(
                "SELECT 1"
            )

        return True
    except psycopg.Error:
        return False


def select_database_url(
    reference: str,
) -> str:
    configured = os.environ.get(
        "SPECGRAPH_DATABASE_URL",
        "",
    ).strip()

    if configured:
        if not test_database_url(
            configured
        ):
            raise RuntimeError(
                "Configured PostgreSQL URL "
                "could not connect"
            )

        return configured

    password = getpass.getpass(
        "Supabase database password: "
    )

    encoded = urllib.parse.quote(
        password,
        safe="",
    )

    pooler_host = os.environ.get(
        "SPECGRAPH_POOLER_HOST",
        (
            "aws-0-us-west-1."
            "pooler.supabase.com"
        ),
    ).strip()

    candidates = [
        (
            f"postgresql://postgres:{encoded}"
            f"@db.{reference}.supabase.co:"
            "5432/postgres?sslmode=require"
        ),
        (
            "postgresql://postgres."
            f"{reference}:{encoded}"
            f"@{pooler_host}:5432/"
            "postgres?sslmode=require"
        ),
    ]

    for candidate in candidates:
        if test_database_url(candidate):
            return candidate

    manual = getpass.getpass(
        (
            "Automatic direct and session-"
            "pooler connections failed. "
            "Paste the full PostgreSQL "
            "connection URL: "
        )
    ).strip()

    if not manual:
        raise RuntimeError(
            "No working database URL supplied"
        )

    if not test_database_url(manual):
        raise RuntimeError(
            "Supplied PostgreSQL URL "
            "could not connect"
        )

    return manual


def main() -> int:
    reference = project_ref()

    if not reference:
        raise SystemExit(
            "Project reference is required"
        )

    database_url = (
        select_database_url(reference)
    )

    owner_id = os.environ.get(
        "SPECGRAPH_OWNER_ID",
        "",
    ).strip()

    temporary_user = False
    service_key = ""

    if not owner_id:
        keys = command_api_keys(
            reference
        )

        if keys is not None:
            service_key = (
                find_service_key(keys)
                or ""
            )

        if not service_key:
            service_key = (
                getpass.getpass(
                    (
                        "Supabase service-role "
                        "or secret server key: "
                    )
                ).strip()
            )

        if not service_key:
            raise RuntimeError(
                "A server-side Supabase key "
                "is required to create the "
                "temporary audit user"
            )

        owner_id, _, _ = (
            create_audit_user(
                reference,
                service_key,
            )
        )

        temporary_user = True

    try:
        report = run_audit(
            database_url,
            owner_id,
        )
    finally:
        if temporary_user:
            delete_audit_user(
                reference,
                service_key,
                owner_id,
            )

    report_path = (
        Path("/data/data/com.termux/files/home/specgraph-hosted-audit.json")
    )

    report_path.write_text(
        json.dumps(
            report,
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )

    report_path.chmod(0o600)

    print(
        json.dumps(
            report,
            indent=2,
            sort_keys=True,
        )
    )

    if not report.get("valid"):
        return 1

    print(
        (
            "HOSTED BACKEND V1 AUDIT PASSED: "
            f"{report_path}"
        ),
        file=sys.stderr,
    )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
