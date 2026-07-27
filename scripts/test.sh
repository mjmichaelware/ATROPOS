#!/data/data/com.termux/files/usr/bin/bash

set -euo pipefail

cd "$HOME/specgraph-foundry"

export PYTHONPATH="$PWD/src"

python -m compileall -q src
python -m unittest discover -s tests -v
python scripts/check_licenses.py
python -m specgraph_foundry init
python -m specgraph_foundry doctor
