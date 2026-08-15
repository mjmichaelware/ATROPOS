"""Turning document text into candidate requirement statements.

Sentence segmentation, modality detection and the research question each
statement implies. Pure text work -- no database, no ids -- which is why it can
be read and tested on its own, and why it is the part worth changing when
atomization quality is the problem.
"""

from __future__ import annotations

import hashlib
import re

from .atom_vocabulary import DIMENSIONS, KIND_RULES

WHITESPACE_PATTERN = re.compile(
    r"\s+"
)


PROHIBITED_PATTERN = re.compile(
    r"\b("
    r"must\s+not|"
    r"shall\s+not|"
    r"should\s+not|"
    r"may\s+not|"
    r"do\s+not|"
    r"does\s+not|"
    r"never|"
    r"cannot|"
    r"can't|"
    r"forbidden|"
    r"prohibited"
    r")\b",
    flags=re.IGNORECASE,
)


MUST_PATTERN = re.compile(
    r"\b("
    r"must|"
    r"required|"
    r"requires|"
    r"needs?\s+to|"
    r"has\s+to|"
    r"have\s+to"
    r")\b",
    flags=re.IGNORECASE,
)


SHALL_PATTERN = re.compile(
    r"\bshall\b",
    flags=re.IGNORECASE,
)


SHOULD_PATTERN = re.compile(
    r"\bshould\b",
    flags=re.IGNORECASE,
)


MAY_PATTERN = re.compile(
    r"\b(may|optional|optionally)\b",
    flags=re.IGNORECASE,
)


HEADING_PATTERN = re.compile(
    r"^\s{0,3}#{1,6}\s+"
)


PREFIX_PATTERN = re.compile(
    r"^\s*(?:(?:[-*+])\s+|(?:\d+[.)])\s+|(?:>)\s*)?"
)


SENTENCE_PATTERN = re.compile(
    r"[^.!?]+(?:[.!?]+(?=\s|$)|$)"
)


WORD_PATTERN = re.compile(
    r"\w+",
    flags=re.UNICODE,
)


def normalize_statement(value: str) -> str:
    return WHITESPACE_PATTERN.sub(
        " ",
        value,
    ).strip()


def classify_modality(
    statement: str,
) -> tuple[str, float]:
    if PROHIBITED_PATTERN.search(statement):
        return "PROHIBITED", 0.99

    if SHALL_PATTERN.search(statement):
        return "SHALL", 0.98

    if MUST_PATTERN.search(statement):
        return "MUST", 0.98

    if SHOULD_PATTERN.search(statement):
        return "SHOULD", 0.95

    if MAY_PATTERN.search(statement):
        return "MAY", 0.92

    return "DECLARATIVE", 0.72


def classify_kind(statement: str) -> str:
    lowered = statement.casefold()

    for kind, keywords in KIND_RULES:
        if any(
            keyword in lowered
            for keyword in keywords
        ):
            return kind

    return "FUNCTIONAL"




def extract_statements(
    raw: bytes,
    sections: list[dict[str, object]],
) -> list[dict[str, object]]:
    text = raw.decode(
        "utf-8",
        errors="strict",
    )

    statements: list[dict[str, object]] = []
    byte_offset = 0
    line_number = 1
    inside_fence = False

    for line in text.splitlines(
        keepends=True
    ):
        line_without_ending = line.rstrip(
            "\r\n"
        )
        stripped = line_without_ending.strip()

        if (
            stripped.startswith("```")
            or stripped.startswith("~~~")
        ):
            inside_fence = not inside_fence
            byte_offset += len(
                line.encode("utf-8")
            )
            line_number += 1
            continue

        if (
            inside_fence
            or not stripped
            or HEADING_PATTERN.match(
                line_without_ending
            )
        ):
            byte_offset += len(
                line.encode("utf-8")
            )
            line_number += 1
            continue

        prefix_match = PREFIX_PATTERN.match(
            line_without_ending
        )

        content_start = (
            prefix_match.end()
            if prefix_match
            else 0
        )

        candidate_text = (
            line_without_ending[
                content_start:
            ]
        )

        for match in SENTENCE_PATTERN.finditer(
            candidate_text
        ):
            segment = match.group(0)

            leading = (
                len(segment)
                - len(segment.lstrip())
            )
            trailing = (
                len(segment)
                - len(segment.rstrip())
            )

            start_character = (
                content_start
                + match.start()
                + leading
            )

            end_character = (
                content_start
                + match.end()
                - trailing
            )

            if end_character <= start_character:
                continue

            exact_quote = (
                line_without_ending[
                    start_character:
                    end_character
                ]
            )

            if len(
                WORD_PATTERN.findall(
                    exact_quote
                )
            ) < 2:
                continue

            canonical = normalize_statement(
                exact_quote
            )

            local_prefix = (
                line_without_ending[
                    :start_character
                ]
            )

            local_statement = (
                line_without_ending[
                    :end_character
                ]
            )

            statement_byte_start = (
                byte_offset
                + len(
                    local_prefix.encode(
                        "utf-8"
                    )
                )
            )

            statement_byte_end = (
                byte_offset
                + len(
                    local_statement.encode(
                        "utf-8"
                    )
                )
            )

            section_id = None

            for section in sections:
                if (
                    int(
                        section["byte_start"]
                    )
                    <= statement_byte_start
                    < int(
                        section["byte_end"]
                    )
                ):
                    section_id = section["id"]
                    break

            modality, confidence = (
                classify_modality(
                    canonical
                )
            )

            statements.append(
                {
                    "ordinal": len(
                        statements
                    ),
                    "section_id": section_id,
                    "kind": classify_kind(
                        canonical
                    ),
                    "modality": modality,
                    "status": "DISCOVERED",
                    "canonical_statement": (
                        canonical
                    ),
                    "exact_quote": (
                        exact_quote
                    ),
                    "byte_start": (
                        statement_byte_start
                    ),
                    "byte_end": (
                        statement_byte_end
                    ),
                    "line_start": (
                        line_number
                    ),
                    "line_end": (
                        line_number
                    ),
                    "source_sha256": (
                        hashlib.sha256(
                            raw[
                                statement_byte_start:
                                statement_byte_end
                            ]
                        ).hexdigest()
                    ),
                    "confidence": confidence,
                }
            )

        byte_offset += len(
            line.encode("utf-8")
        )
        line_number += 1

    return statements
