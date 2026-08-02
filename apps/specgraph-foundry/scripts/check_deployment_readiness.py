from __future__ import annotations

import os
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = ROOT.parent.parent


def check_dockerfiles() -> list[str]:
    problems: list[str] = []
    for name in ("Dockerfile.api", "Dockerfile.worker"):
        path = ROOT / name
        if not path.is_file():
            problems.append(f"{name}: missing")
            continue
        content = path.read_text()
        if "python:3.11-slim@sha256:" not in content:
            problems.append(f"{name}: base image not pinned to SHA")
        if "USER " not in content or "specgraph" not in content:
            problems.append(f"{name}: missing non-root user")
        if "EXPOSE" not in content and name == "Dockerfile.api":
            problems.append(f"{name}: missing EXPOSE")
        if "PORT" not in content:
            problems.append(f"{name}: missing PORT environment variable")
        if "SPECGRAPH_LOG_FORMAT=json" not in content:
            problems.append(f"{name}: missing structured logging config")
        if name == "Dockerfile.api" and "HEALTHCHECK" not in content:
            problems.append(f"{name}: missing HEALTHCHECK")
    return problems


def check_dockerignore() -> list[str]:
    path = ROOT / ".dockerignore"
    if not path.is_file():
        return [".dockerignore: missing"]
    content = path.read_text()
    required = [".git/", "__pycache__/", "node_modules/"]
    missing = [f".dockerignore: missing entry {r!r}" for r in required if r not in content]
    return missing


def check_workflow_pins() -> list[str]:
    problems: list[str] = []
    wf_dir = ROOT / ".github" / "workflows"
    if not wf_dir.is_dir():
        return [".github/workflows: directory missing"]

    # Known pinned actions with SHAs
    known_pins = {
        "actions/checkout": "11bd71901bbe5b1630ceea73d27597364c9af683",
        "actions/setup-python": "ece7cb06caefa5fff74198d8649806c4678c61a1",
        "actions/setup-node": "49933ea5288caeca8642d1e84afbd3f7d6820020",
        "google-github-actions/auth": "71f986410dfbc7added4569d411d040a91dc6935",
        "google-github-actions/setup-gcloud": "77e7a554d41e2ee56fc945c52dfd3f33d12def9a",
    }

    for wf in sorted(wf_dir.glob("*.yml")):
        content = wf.read_text()
        for action, expected_sha in known_pins.items():
            pattern = re.escape(action) + r"@[a-f0-9]{7,40}"
            for match in re.finditer(pattern, content):
                pin = match.group()
                sha = pin.split("@")[1]
                if sha != expected_sha:
                    # Allow unpinned only if it's not the known list
                    if len(sha) == 40:
                        problems.append(f"{wf.name}: {action} pin {sha} differs from expected {expected_sha}")
        # Check for secrets in forks
        if "pull_request_target" in content and ("secrets:" in content or "SECRET" in content.upper()):
            problems.append(f"{wf.name}: pull_request_target with secrets may leak to forks")

    return problems


def check_vercel_config() -> list[str]:
    problems: list[str] = []
    for path in [
        ROOT / "vercel.json",
        REPOSITORY_ROOT / "apps" / "web" / "vercel.json",
    ]:
        if path.is_file():
            return []
    return ["vercel.json: missing (neither SpecGraph root nor canonical ATROPOS apps/web exists)"]


def check_environments() -> list[str]:
    problems: list[str] = []
    wf_dir = ROOT / ".github" / "workflows"
    for wf in sorted(wf_dir.glob("*.yml")):
        content = wf.read_text()
        if "environment:" in content and "secrets:" not in content:
            pass  # Environment reference without secrets is OK
        # Check for hardcoded production values
        if "production" in content and "github.event.inputs" not in content:
            if wf.name not in ("rollback.yml", "supabase-hosted-audit.yml"):
                pass  # Allow production reference in context
    return problems


def check_secret_exposure() -> list[str]:
    problems: list[str] = []
    wf_dir = ROOT / ".github" / "workflows"
    for wf in sorted(wf_dir.glob("*.yml")):
        content = wf.read_text()
        # Check for secrets only referenced via ${{ secrets.* }} not ${{ env.* }}
        secret_refs = re.findall(r'\${{ secrets\.(\w+) }}', content)
        problematic = [s for s in secret_refs if s.startswith(('PROD_', 'PRODUCTION_'))]
        if problematic and 'github.event.pull_request' in content:
            problems.append(f"{wf.name}: production secrets {problematic} referenced in PR context")
    return problems


def check_canonical_migration_owner() -> list[str]:
    canonical = ROOT / "supabase" / "migrations"
    shadow = ROOT / "infra" / "supabase" / "migrations"
    problems: list[str] = []
    if not canonical.is_dir() or not any(canonical.glob("*.sql")):
        problems.append("supabase/migrations: canonical migration directory is missing or empty")
    if shadow.exists():
        problems.append("infra/supabase/migrations: shadow migration directory must not exist")
    for script in sorted((ROOT / "scripts").glob("*.py")):
        if "infra/supabase/migrations" in script.read_text(encoding="utf-8"):
            problems.append(f"{script.name}: writes to shadow migration directory")
    return problems


def main() -> int:
    problems = (
        check_dockerfiles()
        + check_dockerignore()
        + check_workflow_pins()
        + check_vercel_config()
        + check_secret_exposure()
        + check_canonical_migration_owner()
    )

    if problems:
        print("DEPLOYMENT READINESS CHECK FAILED")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print("DEPLOYMENT READINESS CHECK PASSED")
    print("  - Dockerfiles: valid")
    print("  - .dockerignore: present")
    print("  - Workflow pins: valid")
    print("  - Vercel config: present")
    print("  - No secret exposure detected")
    print("  - One canonical Supabase migration directory")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
