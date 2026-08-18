# ATROPOS

A deterministic engine that turns a research document into a verified
execution DAG, and builds against it.

## Install

```
npm install -g @mjmichaelware/atropos
atropos
```

Requires a JVM, 17 or newer. This package is a launcher: it fetches the jar
published by the project's release build and runs it under your `java`.

```
# Termux
pkg install openjdk-21

# Debian / Ubuntu
sudo apt install openjdk-21-jre-headless

# macOS
brew install openjdk@21
```

If you would rather not use npm:

```
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh
```

## Why the jar is downloaded rather than bundled

npm packages get mirrored, cached, and vendored in places nobody tracks, and
a 9 MB binary inside one is copied into every lockfile-pinned install forever.
Fetching from the release keeps one artifact with one checksum — which the
install verifies against the hash the build published, because "it downloaded"
is not the same as "it is what was built".

An offline install does not fail. The package installs, the jar is missing,
and the launcher says how to fetch it.

## Environment

| Variable | What it does |
| --- | --- |
| `ATROPOS_MODEL_<PROVIDER>` | Which model a provider is asked for, e.g. `ATROPOS_MODEL_GROQ`. Overrides the compiled-in default, so a vendor retiring a model is a config change rather than a rebuild. |
| `ATROPOS_INGEST_ROOTS` | Extra directories an `@mention` may read from, separated by `:`. The launch directory is always granted. |
| `ATROPOS_NO_ANIMATION` | Set to skip the opening sequence. |
| `ATROPOS_ASCII` | Draw the interface with ASCII instead of box-drawing characters. |
| `ATROPOS_JAVA_OPTS` | Passed to the JVM, e.g. `-Xmx1g`. |
| `ATROPOS_JAR` | Run a jar you already have instead of the fetched one. |
| `ATROPOS_VERSION` | Which release to fetch at install time. Defaults to `latest`. |

Set them per-run or in your shell profile:

```
ATROPOS_MODEL_GROQ=llama-3.1-8b-instant atropos
```

## Attaching a document

`@path/to/file` inside a prompt attaches it. `.txt`, `.md`, `.docx` and `.pdf`
arrive as text; images arrive described. Ingestion is bounded to the launch
directory plus whatever you granted — on Android, shared storage is reachable
once `termux-setup-storage` has been run.

## Licence

AGPL-3.0-only. Source: https://github.com/mjmichaelware/ATROPOS
