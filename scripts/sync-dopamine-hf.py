#!/usr/bin/env python3
"""
Sync ATROPOS Dopamine Rewards and Memory Telemetry to Hugging Face Dataset.
SPDX-License-Identifier: AGPL-3.0-only
"""
import os
import sys
import json
import base64
import urllib.request
import urllib.error
from pathlib import Path

DATASET_REPO = "mjmichaelware/Atropos-Dopamine"
HF_TOKEN = os.environ.get("HUGGINGFACE_API_KEY") or os.environ.get("HF_TOKEN")

def load_token_from_env_file():
    global HF_TOKEN
    if HF_TOKEN:
        return HF_TOKEN
    env_paths = [
        Path("/data/data/com.termux/files/home/.global_env_keys"),
        Path("/root/.global_env_keys"),
        Path.home() / ".global_env_keys"
    ]
    for p in env_paths:
        if p.exists():
            for line in p.read_text().splitlines():
                if "HUGGINGFACE_API_KEY" in line and "=" in line:
                    val = line.split("=", 1)[1].strip().strip('"').strip("'")
                    if val.startswith("hf_"):
                        HF_TOKEN = val
                        return HF_TOKEN
    return None

def sync_telemetry():
    token = load_token_from_env_file()
    if not token:
        print("[sync-dopamine-hf] Error: No Hugging Face token found in environment or keys files.")
        sys.exit(1)

    repo_root = Path.cwd()
    candidate_roots = [
        repo_root,
        Path("/data/data/com.termux/files/home/ATROPOS"),
        Path.home() / "ATROPOS"
    ]
    for parent in list(repo_root.parents):
        candidate_roots.append(parent)

    target_dir = None
    for cand in candidate_roots:
        if (cand / ".atropos").exists():
            target_dir = cand / ".atropos"
            break

    if not target_dir or not target_dir.exists():
        print(f"[sync-dopamine-hf] Error: .atropos directory not found.")
        sys.exit(1)

    files_to_sync = [
        (target_dir / "autonomy" / "rewards.tsv", "autonomy/rewards.tsv"),
        (target_dir / "memory" / "memory.jsonl", "memory/memory.jsonl"),
    ]

    # Also check evals directory if present
    evals_status = Path("/data/data/com.termux/files/home/atropos-evals/predictions/swe-bench-full/predictions.jsonl")
    if evals_status.exists():
        files_to_sync.append((evals_status, "evals/predictions.jsonl"))

    operations = []
    header = json.dumps({"key": "header", "value": {"summary": "Batch sync dopamine reward telemetry and memory"}})
    operations.append(header)

    synced_count = 0
    for local_path, remote_path in files_to_sync:
        if local_path.exists() and local_path.stat().st_size > 0:
            content_b64 = base64.b64encode(local_path.read_bytes()).decode("ascii")
            op = json.dumps({
                "key": "file",
                "value": {
                    "content": content_b64,
                    "path": remote_path,
                    "encoding": "base64"
                }
            })
            operations.append(op)
            synced_count += 1
            print(f"[sync-dopamine-hf] Prepared {remote_path} ({local_path.stat().st_size} bytes)")

    if synced_count == 0:
        print("[sync-dopamine-hf] No telemetry files found to sync.")
        return

    commit_url = f"https://huggingface.co/api/datasets/{DATASET_REPO}/commit/main"
    payload = ("\n".join(operations) + "\n").encode("utf-8")

    req = urllib.request.Request(
        commit_url,
        data=payload,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/x-ndjson"
        },
        method="POST"
    )

    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print(f"[sync-dopamine-hf] Successfully synced to {DATASET_REPO}")
            print(f"[sync-dopamine-hf] Commit OID: {data.get('commitOid')}")
            print(f"[sync-dopamine-hf] Commit URL: {data.get('commitUrl')}")
    except urllib.error.HTTPError as e:
        print(f"[sync-dopamine-hf] HTTP Error {e.code}: {e.read().decode('utf-8')}")
        sys.exit(1)
    except Exception as e:
        print(f"[sync-dopamine-hf] Network/Sync Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    sync_telemetry()
