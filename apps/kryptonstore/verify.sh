#!/usr/bin/env sh
set -eu
python3 -m unittest discover -s . -p 'test_*.py'
