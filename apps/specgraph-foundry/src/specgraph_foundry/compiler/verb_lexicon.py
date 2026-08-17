"""A closed verb lexicon, for splitting a statement that states two actions.

The source documents ATROPOS compiles state the rule themselves:

    One atom = one symbol or one acceptance predicate or one user-visible
    affordance. If it has two verbs, split it. ... If an atom still contains
    "and", split again.

Following that rule needs to know where the verbs are, and the choice of how
is a determinism decision before it is an accuracy one.

## Why a word list and not a tagger

A part-of-speech tagger is more accurate on arbitrary English and cannot be
used here. Its models change between library versions, so the same document
would atomize into different atoms on two machines and neither run could be
reproduced from the other -- which is the one thing this compiler may not do.
It also would not install on the aarch64 phone ATROPOS is built to run on.

A closed list is weaker on rare verbs and has the property that matters: the
same bytes in, the same atoms out, on every machine, forever. When it misses a
verb the miss is visible and fixable by adding a word, rather than being an
opaque model judgement.

The lexicon is versioned and hashed so a run records which vocabulary produced
its atoms. Adding a word changes the hash, which is correct: it is a different
compiler.
"""

from __future__ import annotations

import hashlib
import re

LEXICON_VERSION = "1"

# Verbs that appear in specifications, as bare stems. Morphology below covers
# the inflected forms, so "render" also matches "renders", "rendered",
# "rendering".
#
# Deliberately excludes the copulas (be/is/are/was) and the modals: "the panel
# is visible" states one thing, and counting "is" as an action would split
# every clause in the document.
ACTION_VERBS = frozenset({
    # produce / change
    "add", "apply", "append", "build", "cache", "capture", "clear", "compile",
    "compose", "compute", "create", "declare", "define", "delete", "derive",
    "drop", "emit", "encode", "decode", "extract", "generate", "implement",
    "insert", "install", "load", "merge", "mutate", "normalize", "normalise",
    "parse", "persist", "produce", "publish", "purge", "record", "register",
    "remove", "render", "replace", "reset", "resolve", "restore", "rewrite",
    "save", "serialize", "serialise", "set", "store", "transform", "update",
    "write",
    # move / route
    "bind", "call", "connect", "dispatch", "export", "fetch", "forward",
    "import", "invoke", "issue", "link", "map", "mount", "open", "point",
    "post", "pull", "push", "queue", "read", "receive", "request", "return",
    "route", "send", "serve", "spawn", "stream", "submit", "surface", "wire",
    # decide / guard
    "accept", "allow", "assert", "attest", "audit", "authorize", "authorise",
    "block", "bound", "check", "confirm", "deny", "detect", "enforce",
    "ensure", "escalate", "evaluate", "fail", "filter", "gate", "guard",
    "hold", "ignore", "limit", "lock", "match", "permit", "prevent", "prove",
    "refuse", "reject", "require", "restrict", "retry", "revoke", "scan",
    "skip", "stop", "validate", "verify",
    # present / observe
    "announce", "collapse", "count", "describe", "display", "draw", "echo",
    "expand", "explain", "expose", "highlight", "hide", "indicate", "label",
    "list", "log", "measure", "monitor", "name", "observe", "print", "report",
    "reveal", "show", "state", "trace", "warn", "watch",
    # organise / lifecycle
    "advance", "assign", "cancel", "close", "complete", "continue", "execute",
    "finish", "group", "handle", "init", "initialize", "initialise", "order",
    "pause", "plan", "prioritize", "prioritise", "promote", "recover",
    "release", "rescan", "restart", "resume", "revert", "rollback", "run",
    "schedule", "select", "sort", "split", "start", "switch", "sync", "track",
    "trigger",
})

# Suffixes stripped to reach a stem. Ordered longest first so "ing" is tried
# before "g" would be, and applied one at a time -- a word is a verb if any
# single strip reaches the lexicon.
_SUFFIXES = ("ing", "ed", "es", "s")

_WORD_RE = re.compile(r"[A-Za-z][A-Za-z-]*")


def is_action_verb(word: str) -> bool:
    """Whether `word` is a lexicon verb in any of its inflected forms."""
    lowered = word.lower().strip("-")
    if lowered in ACTION_VERBS:
        return True
    for suffix in _SUFFIXES:
        if lowered.endswith(suffix):
            stem = lowered[: -len(suffix)]
            if stem in ACTION_VERBS:
                return True
            # "applies" -> "appli" -> "apply"; "cached" -> "cach" -> "cache"
            if stem.endswith("i") and stem[:-1] + "y" in ACTION_VERBS:
                return True
            if stem + "e" in ACTION_VERBS:
                return True
            # "wired" -> "wir" + doubled-consonant forms like "wrapped"
            if stem and stem[-1] == stem[-2:-1] and stem[:-1] in ACTION_VERBS:
                return True
    return False


def verb_positions(text: str):
    """Character offsets of every lexicon verb in `text`, in order."""
    return [m.start() for m in _WORD_RE.finditer(text) if is_action_verb(m.group())]


def verb_count(text: str) -> int:
    return len(verb_positions(text))


def lexicon_fingerprint() -> str:
    """A hash of the vocabulary, so a run records what produced its atoms."""
    payload = LEXICON_VERSION + "\n" + "\n".join(sorted(ACTION_VERBS))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]
