#!/data/data/com.termux/files/usr/bin/bash

set -euo pipefail

cd "$HOME/specgraph-foundry"

if [ -f .env ]
then
  set -a
  . ./.env
  set +a
fi

export PYTHONPATH="$PWD/src"

exec python -m specgraph_foundry.http_api
