#!/usr/bin/env bash
set -euo pipefail

ROOT="/data/data/com.termux/files/home/specgraph-foundry"
VENV="${SPECGRAPH_AUDIT_VENV:-$HOME/.venvs/specgraph-foundry}"

cd "$ROOT"

if [ ! -x "$VENV/bin/python" ]; then
  apt-get update
  apt-get install -y \
    python3 \
    python3-venv \
    python3-pip \
    libpq5

  mkdir -p "$(dirname "$VENV")"
  python3 -m venv "$VENV"
fi

"$VENV/bin/python" -m pip install \
  --upgrade \
  pip \
  setuptools \
  wheel

"$VENV/bin/python" -m pip install \
  -e '.[postgres]'

export PYTHONPATH="$ROOT/src"

exec "$VENV/bin/python" \
  scripts/hosted_release_runner.py
