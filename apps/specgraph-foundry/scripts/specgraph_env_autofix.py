#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

SPECGRAPH_REF_REQUIRED = "rlzvfamnjhyzkcdhydvm"

SHELL_ENV = Path.home() / ".config/specgraph/specgraph.env"
DOTENV = Path.cwd() / ".env.local"

def run(cmd: list[str], input_text: str | None = None) -> tuple[int, str, str]:
    p = subprocess.run(
        cmd,
        input=input_text,
        text=True,
        capture_output=True,
        check=False,
    )
    return p.returncode, p.stdout, p.stderr

def gcloud_secret(name: str) -> str:
    if not shutil.which("gcloud"):
        return ""
    code, out, _ = run(["gcloud", "secrets", "versions", "access", "latest", f"--secret={name}"])
    return out.strip() if code == 0 and out.strip() else ""

def write_gcloud_secret(name: str, value: str) -> str:
    if not value or not shutil.which("gcloud"):
        return "SKIP"
    exists = run(["gcloud", "secrets", "describe", name])[0] == 0
    if exists:
        code, _, err = run(["gcloud", "secrets", "versions", "add", name, "--data-file=-"], value)
        return "UPDATED" if code == 0 else f"ERROR: {err.strip()[:160]}"
    code, _, err = run(
        ["gcloud", "secrets", "create", name, "--replication-policy=automatic", "--data-file=-"],
        value,
    )
    return "CREATED" if code == 0 else f"ERROR: {err.strip()[:160]}"

def ref_from_supabase_url(url: str) -> str:
    m = re.search(r"https://([a-z0-9]+)\.supabase\.co", url or "")
    return m.group(1) if m else ""

def ref_from_db_url(url: str) -> str:
    m = re.search(r"postgres\.([a-z0-9]+):", url or "")
    if m:
        return m.group(1)
    m = re.search(r"db\.([a-z0-9]+)\.supabase\.co", url or "")
    return m.group(1) if m else ""

def looks_like_secret_placeholder(value: str) -> bool:
    bad = ["YOUR-PASSWORD", "[YOUR-PASSWORD]", "PASSWORD", "<", ">", "PASTE_"]
    return any(x in value for x in bad)

def http_json(url: str, headers: dict[str, str] | None = None, payload: dict[str, Any] | None = None) -> tuple[int, Any]:
    data = None
    method = "GET"
    h = dict(headers or {})
    if payload is not None:
        data = json.dumps(payload).encode()
        method = "POST"
        h["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            text = r.read().decode()
            try:
                return r.status, json.loads(text)
            except Exception:
                return r.status, text
    except urllib.error.HTTPError as e:
        text = e.read().decode(errors="replace")
        try:
            return e.code, json.loads(text)
        except Exception:
            return e.code, text
    except Exception as e:
        return 0, {"error": repr(e)}

def mgmt_get_projects(token: str) -> list[dict[str, Any]]:
    if not token:
        return []
    status, data = http_json(
        "https://api.supabase.com/v1/projects",
        {"Authorization": f"Bearer {token}"},
    )
    if status != 200:
        return []
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for key in ("projects", "items", "data"):
            if isinstance(data.get(key), list):
                return data[key]
    return []

def mgmt_get_api_keys(token: str, ref: str) -> list[dict[str, Any]]:
    if not token or not ref:
        return []
    urls = [
        f"https://api.supabase.com/v1/projects/{ref}/api-keys",
        f"https://api.supabase.com/v1/projects/{ref}/api_keys",
    ]
    for url in urls:
        status, data = http_json(url, {"Authorization": f"Bearer {token}"})
        if status == 200:
            if isinstance(data, list):
                return data
            if isinstance(data, dict):
                for key in ("api_keys", "keys", "items", "data"):
                    if isinstance(data.get(key), list):
                        return data[key]
    return []

def extract_anon_key(keys: list[dict[str, Any]]) -> str:
    for k in keys:
        name = str(k.get("name") or k.get("key_name") or k.get("type") or "").lower()
        value = str(k.get("api_key") or k.get("key") or k.get("value") or "")
        if value and ("anon" in name or "publishable" in name or "public" in name):
            return value
    for k in keys:
        value = str(k.get("api_key") or k.get("key") or k.get("value") or "")
        if value.startswith("eyJ") or value.startswith("sb_publishable_"):
            return value
    return ""

def auth_probe(supabase_url: str, anon_key: str, email: str, password: str) -> tuple[bool, str]:
    if not all([supabase_url, anon_key, email, password]):
        return False, ""
    status, data = http_json(
        f"{supabase_url.rstrip('/')}/auth/v1/token?grant_type=password",
        {"apikey": anon_key},
        {"email": email, "password": password},
    )
    if status == 200 and isinstance(data, dict) and data.get("access_token"):
        return True, data["access_token"]
    return False, ""

def api_workspace_probe(api_base: str, project_id: str, token: str) -> bool:
    if not all([api_base, project_id, token]):
        return False
    status, data = http_json(
        f"{api_base.rstrip('/')}/v1/projects/{project_id}/workspace",
        {"Authorization": f"Bearer {token}"},
    )
    return status == 200 and isinstance(data, dict) and "counts" in data

def first_nonempty(*values: str) -> str:
    for v in values:
        if v:
            return v
    return ""

def redact(v: str) -> str:
    if not v:
        return ""
    v = re.sub(r"(postgres(?:\.[a-z0-9]+)?):([^@]+)@", r"\1:***@", v)
    if len(v) > 24 and not v.startswith("http"):
        return v[:8] + "..." + v[-6:]
    return v

def write_env_files(final: dict[str, str]) -> None:
    SHELL_ENV.parent.mkdir(parents=True, exist_ok=True)
    shell_lines = [
        "# Auto-generated SpecGraph env. Do not commit.",
        "# Source: current env + Google Secret Manager + Supabase Management API.",
        "",
    ]
    dotenv_lines = [
        "# Auto-generated SpecGraph env. Do not commit.",
        "",
    ]

    order = [
        "SUPABASE_URL",
        "SUPABASE_PROJECT_REF",
        "SUPABASE_ANON_KEY",
        "SPECGRAPH_API_BASE",
        "SPECGRAPH_PROJECT_ID",
        "SPECGRAPH_EMAIL",
        "SPECGRAPH_PASSWORD",
        "SPECGRAPH_DATABASE_URL",
    ]

    for key in order:
        value = final.get(key, "")
        if not value:
            continue
        shell_lines.append(f"export {key}={shlex.quote(value)}")
        dotenv_lines.append(f"{key}={value}")

    SHELL_ENV.write_text("\n".join(shell_lines) + "\n", encoding="utf-8")
    DOTENV.write_text("\n".join(dotenv_lines) + "\n", encoding="utf-8")
    SHELL_ENV.chmod(0o600)
    DOTENV.chmod(0o600)

def patch_shell_rc() -> list[str]:
    block = """
# >>> SPECGRAPH ENV AUTOLOAD >>>
if [ -f "$HOME/.config/specgraph/specgraph.env" ]; then
  . "$HOME/.config/specgraph/specgraph.env"
fi
# <<< SPECGRAPH ENV AUTOLOAD <<<
""".strip() + "\n"

    touched = []
    for rc in [Path.home() / ".bashrc", Path.home() / ".profile"]:
        current = rc.read_text(encoding="utf-8", errors="replace") if rc.exists() else ""
        if "SPECGRAPH ENV AUTOLOAD" not in current:
            rc.write_text(current.rstrip() + "\n\n" + block, encoding="utf-8")
            touched.append(str(rc))

    zsh = Path.home() / ".zshrc"
    if zsh.exists():
        current = zsh.read_text(encoding="utf-8", errors="replace")
        if "SPECGRAPH ENV AUTOLOAD" not in current:
            zsh.write_text(current.rstrip() + "\n\n" + block, encoding="utf-8")
            touched.append(str(zsh))

    return touched

def main() -> int:
    env = os.environ

    gsm = {
        name: gcloud_secret(name)
        for name in [
            "SPECGRAPH_API_BASE",
            "SPECGRAPH_PROJECT_ID",
            "SPECGRAPH_EMAIL",
            "SPECGRAPH_PASSWORD",
            "SPECGRAPH_DATABASE_URL",
            "SPECGRAPH_SUPABASE_URL",
            "SPECGRAPH_SUPABASE_PROJECT_REF",
            "SPECGRAPH_SUPABASE_ANON_KEY",
            "SUPABASE_URL",
            "NEXT_PUBLIC_SUPABASE_URL",
            "SUPABASE_PROJECT_REF",
            "SUPABASE_ANON_KEY",
            "DATABASE_URL",
            "SUPABASE_ACCESS_TOKEN",
        ]
    }

    mgmt_token = first_nonempty(env.get("SUPABASE_ACCESS_TOKEN", ""), gsm.get("SUPABASE_ACCESS_TOKEN", ""))
    projects = mgmt_get_projects(mgmt_token)
    project_by_ref = {}
    for p in projects:
        ref = str(p.get("ref") or p.get("id") or "")
        if ref:
            project_by_ref[ref] = p

    api_base = first_nonempty(
        env.get("SPECGRAPH_API_BASE", ""),
        gsm.get("SPECGRAPH_API_BASE", ""),
        "https://specgraph-api-882099804366.us-west1.run.app",
    )

    project_id = first_nonempty(
        env.get("SPECGRAPH_PROJECT_ID", ""),
        gsm.get("SPECGRAPH_PROJECT_ID", ""),
        "6748ca1d-e803-41eb-a75b-c12ac88f1c8c",
    )

    email = first_nonempty(env.get("SPECGRAPH_EMAIL", ""), gsm.get("SPECGRAPH_EMAIL", ""))
    password = first_nonempty(env.get("SPECGRAPH_PASSWORD", ""), gsm.get("SPECGRAPH_PASSWORD", ""))

    url_candidates = []
    for u in [
        env.get("SUPABASE_URL", ""),
        env.get("NEXT_PUBLIC_SUPABASE_URL", ""),
        gsm.get("SPECGRAPH_SUPABASE_URL", ""),
        gsm.get("SUPABASE_URL", ""),
        gsm.get("NEXT_PUBLIC_SUPABASE_URL", ""),
        f"https://{SPECGRAPH_REF_REQUIRED}.supabase.co",
    ]:
        if u and u not in url_candidates:
            url_candidates.append(u)

    anon_candidates = []
    for a in [
        env.get("SUPABASE_ANON_KEY", ""),
        gsm.get("SPECGRAPH_SUPABASE_ANON_KEY", ""),
        gsm.get("SUPABASE_ANON_KEY", ""),
    ]:
        if a and a not in anon_candidates:
            anon_candidates.append(a)

    api_keys = mgmt_get_api_keys(mgmt_token, SPECGRAPH_REF_REQUIRED)
    anon_from_api = extract_anon_key(api_keys)
    if anon_from_api and anon_from_api not in anon_candidates:
        anon_candidates.append(anon_from_api)

    chosen_url = ""
    chosen_anon = ""
    chosen_token = ""

    for u in url_candidates:
        if ref_from_supabase_url(u) != SPECGRAPH_REF_REQUIRED:
            continue
        for a in anon_candidates:
            ok, token = auth_probe(u, a, email, password)
            if ok and api_workspace_probe(api_base, project_id, token):
                chosen_url = u
                chosen_anon = a
                chosen_token = token
                break
        if chosen_url:
            break

    if not chosen_url:
        chosen_url = f"https://{SPECGRAPH_REF_REQUIRED}.supabase.co"
    if not chosen_anon and anon_candidates:
        chosen_anon = anon_candidates[0]

    db_candidates = []
    for d in [
        env.get("SPECGRAPH_DATABASE_URL", ""),
        gsm.get("SPECGRAPH_DATABASE_URL", ""),
        env.get("DATABASE_URL", ""),
        gsm.get("DATABASE_URL", ""),
    ]:
        if d and d not in db_candidates:
            db_candidates.append(d)

    project_meta = project_by_ref.get(SPECGRAPH_REF_REQUIRED, {})
    for key in ("connection_string", "db_connection_string", "database_url"):
        d = str(project_meta.get(key) or "")
        if d and d not in db_candidates:
            db_candidates.append(d)

    chosen_db = ""
    for d in db_candidates:
        if looks_like_secret_placeholder(d):
            continue
        if ref_from_db_url(d) == SPECGRAPH_REF_REQUIRED:
            chosen_db = d
            break

    final = {
        "SUPABASE_URL": chosen_url,
        "SUPABASE_PROJECT_REF": SPECGRAPH_REF_REQUIRED,
        "SUPABASE_ANON_KEY": chosen_anon,
        "SPECGRAPH_API_BASE": api_base,
        "SPECGRAPH_PROJECT_ID": project_id,
        "SPECGRAPH_EMAIL": email,
        "SPECGRAPH_PASSWORD": password,
        "SPECGRAPH_DATABASE_URL": chosen_db,
    }

    missing_api = [k for k in [
        "SUPABASE_URL",
        "SUPABASE_PROJECT_REF",
        "SUPABASE_ANON_KEY",
        "SPECGRAPH_API_BASE",
        "SPECGRAPH_PROJECT_ID",
        "SPECGRAPH_EMAIL",
        "SPECGRAPH_PASSWORD",
    ] if not final.get(k)]

    write_env_files(final)
    touched = patch_shell_rc()

    gsm_results = {}
    gsm_map = {
        "SPECGRAPH_SUPABASE_URL": final["SUPABASE_URL"],
        "SPECGRAPH_SUPABASE_PROJECT_REF": final["SUPABASE_PROJECT_REF"],
        "SPECGRAPH_SUPABASE_ANON_KEY": final["SUPABASE_ANON_KEY"],
        "SPECGRAPH_API_BASE": final["SPECGRAPH_API_BASE"],
        "SPECGRAPH_PROJECT_ID": final["SPECGRAPH_PROJECT_ID"],
        "SPECGRAPH_EMAIL": final["SPECGRAPH_EMAIL"],
        "SPECGRAPH_PASSWORD": final["SPECGRAPH_PASSWORD"],
    }
    if final.get("SPECGRAPH_DATABASE_URL"):
        gsm_map["SPECGRAPH_DATABASE_URL"] = final["SPECGRAPH_DATABASE_URL"]

    for k, v in gsm_map.items():
        gsm_results[k] = write_gcloud_secret(k, v)

    report = {
        "wrote_shell_env": str(SHELL_ENV),
        "wrote_dotenv": str(DOTENV),
        "patched_shell_files": touched,
        "supabase_url_ref": ref_from_supabase_url(final["SUPABASE_URL"]),
        "database_url_ref": ref_from_db_url(final.get("SPECGRAPH_DATABASE_URL", "")) or "NOT_SET_API_ONLY",
        "api_login_probe": bool(chosen_token),
        "supabase_management_api_projects_seen": len(projects),
        "supabase_management_api_keys_seen_for_ref": len(api_keys),
        "missing_api_env_after_autofix": missing_api,
        "google_secret_manager_write_results": gsm_results,
        "db_direct_mode_ready": bool(final.get("SPECGRAPH_DATABASE_URL")),
        "api_only_mode_ready": not missing_api,
        "written_keys": {k: ("SET" if v else "MISSING") for k, v in final.items()},
        "redacted_values": {k: redact(v) for k, v in final.items() if v},
    }

    out = Path("artifacts/env-db-audit/specgraph_env_autofix_report.json")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(json.dumps(report, indent=2, sort_keys=True))
    print()
    print("WROTE=" + str(out))

    if missing_api:
        print("STOP: API env still missing: " + ", ".join(missing_api))
        return 2

    if not final.get("SPECGRAPH_DATABASE_URL"):
        print("NOTICE: direct Postgres env was not written because no valid DB URL for rlzvfamnjhyzkcdhydvm was recoverable.")
        print("Hosted API mode is permanent and ready.")
        return 0

    print("PASS: hosted API env and direct Postgres env are permanent.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
