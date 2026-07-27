# Contributing

- One file has one narrow responsibility.
- Every behavior requires a testable contract.
- No placeholder or fake-success implementations.
- No provider verifies its own work.
- Source requirements retain exact provenance.
- Research conclusions remain distinct from source authority.
- New dependencies require license review.
- Secrets never enter commits, fixtures, logs, or examples.

Run the foundation gate:

```bash
export PYTHONPATH="$PWD/src"
./scripts/test.sh
```
