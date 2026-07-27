# Dependency License Policy

Every production dependency must pass a license-admission gate.

Normally acceptable after verification:

- Apache-2.0
- MIT
- BSD-2-Clause
- BSD-3-Clause
- ISC
- 0BSD

Requires explicit review:

- MPL-2.0
- EPL-2.0
- LGPL licenses
- dual-licensed packages
- fonts
- models
- datasets
- media assets
- hosted API terms

Forbidden without a deliberate project-level decision:

- unknown-license dependencies
- noncommercial restrictions
- field-of-use restrictions
- copied code without provenance
- packages without exact versions
- generated code copied from unidentified third-party sources

Current admitted runtime dependency:

- `pypdf==4.3.1`
  License: BSD-3-Clause
  Reason: mature pure-Python PDF parser for bounded text extraction

Current admitted web exceptions:

- `elkjs==0.11.1`
  License: EPL-2.0
  Reason: required deterministic graph-layout compatibility package for the accepted frontend architecture; no source is copied into repository code
- `axe-core==4.12.1` and `@axe-core/playwright==4.12.1`
  License: MPL-2.0
  Reason: required accessibility test infrastructure for the web foundation; used as npm dependencies only
