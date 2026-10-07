#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

def run(cmd: list[str]) -> tuple[int, str, str]:
    p = subprocess.run(cmd, text=True, capture_output=True, check=False)
    return p.returncode, p.stdout, p.stderr

def redact(value: str) -> str:
    if not value:
        return ""
    value = re.sub(r"(postgres(?:\.[a-z0-9]+)?):([^@]+)@", r"\1:***@", value)
    value = re.sub(r"(Bearer\s+)[A-Za-z0-9._~-]+", r"\1***", value)
    value = re.sub(r"(sbp_)[A-Za-z0-9_]+", r"\1***", value)
    value = re.sub(r"(eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.)([A-Za-z0-9_-]+)", r"\1***", value)
    return value

def supabase_url_ref(url: str) -> str | None:
    m = re.search(r"https://([a-z0-9]+)\.supabase\.co", url or "")
    return m.group(1) if m else None

def db_url_ref(url: str) -> str | None:
    m = re.search(r"postgres\.([a-z0-9]+):", url or "")
    if m:
        return m.group(1)
    m = re.search(r"db\.([a-z0-9]+)\.supabase\.co", url or "")
    if m:
        return m.group(1)
    return None

def secret_value(secret_name: str) -> str | None:
    if not shutil.which("gcloud"):
        return None
    code, out, err = run(["gcloud", "secrets", "versions", "access", "latest", f"--secret={secret_name}"])
    if code == 0 and out.strip():
        return out.strip()
    return None

def main() -> int:
    root = Path.cwd()
    report: dict[str, object] = {
        "root": str(root),
        "commands": {
            "gcloud": bool(shutil.which("gcloud")),
            "supabase": bool(shutil.which("supabase")),
            "python": sys.executable,
        },
        "environment": {},
        "secret_manager": {},
        "ref_alignment": {},
        "schema": {},
        "api": {},
        "warnings": [],
    }

    names = [
        "SUPABASE_URL",
        "SUPABASE_PROJECT_REF",
        "SPECGRAPH_API_BASE",
        "SPECGRAPH_PROJECT_ID",
        "SPECGRAPH_OWNER_ID",
        "DATABASE_URL",
        "SPECGRAPH_DATABASE_URL",
        "SUPABASE_ANON_KEY",
        "SPECGRAPH_EMAIL",
        "SPECGRAPH_PASSWORD",
    ]

    env_values = {name: os.environ.get(name, "") for name in names}

    for name, value in env_values.items():
        report["environment"][name] = {
            "set": bool(value),
            "redacted": redact(value) if name in {"SUPABASE_URL", "SPECGRAPH_API_BASE", "DATABASE_URL", "SPECGRAPH_DATABASE_URL"} else ("SET" if value else "MISSING"),
        }

    gsm_names = [
        "SUPABASE_URL",
        "SUPABASE_PROJECT_REF",
        "SPECGRAPH_API_BASE",
        "SPECGRAPH_PROJECT_ID",
        "SPECGRAPH_OWNER_ID",
        "SPECGRAPH_DATABASE_URL",
        "DATABASE_URL",
        "SUPABASE_ANON_KEY",
        "SPECGRAPH_EMAIL",
        "SPECGRAPH_PASSWORD",
    ]

    gsm_values: dict[str, str] = {}
    for name in gsm_names:
        value = secret_value(name)
        if value:
            gsm_values[name] = value
            report["secret_manager"][name] = {
                "available": True,
                "redacted": redact(value) if "URL" in name or name == "DATABASE_URL" else "SET",
            }
        else:
            report["secret_manager"][name] = {"available": False}

    effective_supabase_url = env_values.get("SUPABASE_URL") or gsm_values.get("SUPABASE_URL", "")
    effective_db_url = env_values.get("SPECGRAPH_DATABASE_URL") or env_values.get("DATABASE_URL") or gsm_values.get("SPECGRAPH_DATABASE_URL", "") or gsm_values.get("DATABASE_URL", "")

    api_ref = supabase_url_ref(effective_supabase_url)
    db_ref = db_url_ref(effective_db_url)
    explicit_ref = env_values.get("SUPABASE_PROJECT_REF") or gsm_values.get("SUPABASE_PROJECT_REF", "")

    report["ref_alignment"] = {
        "supabase_url_ref": api_ref or "UNKNOWN",
        "database_url_ref": db_ref or "UNKNOWN",
        "supabase_project_ref": explicit_ref or "UNKNOWN",
        "url_and_db_match": bool(api_ref and db_ref and api_ref == db_ref),
        "explicit_ref_matches_url": bool(explicit_ref and api_ref and explicit_ref == api_ref),
        "explicit_ref_matches_db": bool(explicit_ref and db_ref and explicit_ref == db_ref),
    }

    if api_ref and db_ref and api_ref != db_ref:
        report["warnings"].append("SUPABASE_URL and SPECGRAPH_DATABASE_URL/DATABASE_URL point at different Supabase project refs.")
    if explicit_ref and api_ref and explicit_ref != api_ref:
        report["warnings"].append("SUPABASE_PROJECT_REF does not match SUPABASE_URL ref.")
    if explicit_ref and db_ref and explicit_ref != db_ref:
        report["warnings"].append("SUPABASE_PROJECT_REF does not match database URL ref.")

    try:
        from specgraph_foundry.database import Database
        required = sorted(getattr(Database, "REQUIRED_POSTGRES_TABLES", []))
        report["schema"]["required_postgres_tables_from_code"] = required
    except Exception as e:
        report["schema"]["code_import_error"] = repr(e)
        required = []

    if effective_db_url:
        try:
            import psycopg
            from psycopg.rows import dict_row
            with psycopg.connect(effective_db_url, row_factory=dict_row, prepare_threshold=None, connect_timeout=10) as conn:
                tables = conn.execute(
                    """
                    select table_name
                    from information_schema.tables
                    where table_schema = 'public'
                    order by table_name
                    """
                ).fetchall()
                actual = [r["table_name"] for r in tables]
                report["schema"]["public_tables"] = actual
                report["schema"]["missing_required_tables"] = sorted(set(required) - set(actual))
                report["schema"]["extra_public_tables"] = sorted(set(actual) - set(required))

                migration_tables = conn.execute(
                    """
                    select table_schema, table_name
                    from information_schema.tables
                    where table_name = 'schema_migrations'
                    order by table_schema
                    """
                ).fetchall()
                report["schema"]["migration_tables"] = [dict(r) for r in migration_tables]

                for schema in ("supabase_migrations", "public"):
                    try:
                        rows = conn.execute(
                            f"select version from {schema}.schema_migrations order by version"
                        ).fetchall()
                        report["schema"][f"{schema}.schema_migrations"] = [r["version"] for r in rows]
                    except Exception as e:
                        report["schema"][f"{schema}.schema_migrations_error"] = str(e)

                project_id = env_values.get("SPECGRAPH_PROJECT_ID") or gsm_values.get("SPECGRAPH_PROJECT_ID", "")
                if project_id:
                    try:
                        row = conn.execute(
                            "select id, owner_id, slug, name from public.projects where id = %s",
                            (project_id,),
                        ).fetchone()
                        report["schema"]["specgraph_project_found"] = bool(row)
                        if row:
                            report["schema"]["specgraph_project"] = {k: str(row[k]) for k in row.keys()}
                    except Exception as e:
                        report["schema"]["project_lookup_error"] = str(e)

        except Exception as e:
            report["schema"]["postgres_connection_error"] = str(e)
    else:
        report["schema"]["postgres_connection_error"] = "No database URL available."

    migrations = sorted(str(p.name) for p in Path("supabase/migrations").glob("*.sql"))
    report["schema"]["local_migration_files"] = migrations

    out = Path("artifacts/env-db-audit/specgraph_env_db_audit.json")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(json.dumps(report, indent=2, sort_keys=True))
    print()
    print(f"WROTE={out}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
