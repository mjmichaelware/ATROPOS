#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
. "$HOME/.atropos/secrets/r2.env"
exec python3 - "$1" << 'PY'
import sys, os, subprocess, signal
from pathlib import Path

tag = sys.argv[1] if len(sys.argv) > 1 else ""
if not tag:
    print("usage: lh-get.sh PATH_TAG"); sys.exit(2)

home = Path.home()
paths = home / ".atropos/lakehouse/index/paths.txt"
index = home / ".atropos/lakehouse/index/objects.tsv"
bucket = os.environ.get("R2_BUCKET", "atropos-lakehouse")

path_set = {ln.strip() for ln in paths.read_text().splitlines() if ln.strip()}
if tag not in path_set:
    print("REJECT"); sys.exit(1)

hit = None
for ln in index.read_text().splitlines():
    parts = ln.split("\t")
    if len(parts) >= 2 and parts[1].strip() == tag:
        hit = parts[0].strip()
        break

if not hit:
    print("MISS"); sys.exit(0)

print(f"HIT\t{hit}\t{tag}", flush=True)
p = subprocess.run(["rclone", "cat", f"r2:{bucket}/{hit}"])
# 141 = SIGPIPE when consumer closes early (head) — treat as success
if p.returncode in (0, 141, -signal.SIGPIPE):
    sys.exit(0)
sys.exit(p.returncode or 1)
PY
