"""Splitting a document into sections and byte-bounded chunks.

Pure text and byte work: where sections begin, where a chunk may end without
cutting a UTF-8 sequence in half, and whether the chunks cover the document with
no gap or overlap.

No database, no ids. Its own module because these are the functions whose
correctness decides whether an atom's coordinates point at the right bytes, and
they are worth reading without a service around them.
"""

from __future__ import annotations

from bisect import bisect_right

from .errors import ValidationError
import hashlib
import re

HEADING_PATTERN = re.compile(
    r"^\s{0,3}(#{1,6})\s+(.+?)\s*$"
)

def line_count(raw: bytes) -> int:
    count = raw.count(b"\n")

    if not raw.endswith(b"\n"):
        count += 1

    return max(count, 1)


def line_starts(raw: bytes) -> list[int]:
    starts = [0]

    for index, byte in enumerate(raw):
        if byte == 10 and index + 1 < len(raw):
            starts.append(index + 1)

    return starts


def line_number(
    starts: list[int],
    byte_offset: int,
) -> int:
    return bisect_right(
        starts,
        byte_offset,
    )


def ending_line_number(
    starts: list[int],
    byte_end: int,
) -> int:
    return line_number(
        starts,
        max(0, byte_end - 1),
    )


def safe_utf8_end(
    raw: bytes,
    start: int,
    desired_end: int,
) -> int:
    end = min(desired_end, len(raw))

    while (
        end > start
        and end < len(raw)
        and raw[end] & 0b11000000 == 0b10000000
    ):
        end -= 1

    if end == start:
        end = min(start + 1, len(raw))

        while (
            end < len(raw)
            and raw[end] & 0b11000000 == 0b10000000
        ):
            end += 1

    return end


def detect_sections(
    raw: bytes,
) -> list[dict[str, object]]:
    text = raw.decode("utf-8", errors="strict")
    starts = line_starts(raw)
    headings: list[dict[str, object]] = []
    byte_offset = 0

    for line in text.splitlines(keepends=True):
        stripped = line.rstrip("\r\n")
        match = HEADING_PATTERN.match(stripped)

        if match:
            headings.append(
                {
                    "byte_start": byte_offset,
                    "title": match.group(2).strip(),
                    "heading_level": len(match.group(1)),
                }
            )

        byte_offset += len(
            line.encode("utf-8")
        )

    if not headings:
        return [
            {
                "ordinal": 0,
                "title": "Document",
                "heading_level": None,
                "byte_start": 0,
                "byte_end": len(raw),
                "line_start": 1,
                "line_end": ending_line_number(
                    starts,
                    len(raw),
                ),
            }
        ]

    sections: list[dict[str, object]] = []
    ordinal = 0

    if int(headings[0]["byte_start"]) > 0:
        first_start = int(
            headings[0]["byte_start"]
        )

        sections.append(
            {
                "ordinal": ordinal,
                "title": "Preamble",
                "heading_level": None,
                "byte_start": 0,
                "byte_end": first_start,
                "line_start": 1,
                "line_end": ending_line_number(
                    starts,
                    first_start,
                ),
            }
        )
        ordinal += 1

    for index, heading in enumerate(headings):
        start = int(heading["byte_start"])

        if index + 1 < len(headings):
            end = int(
                headings[index + 1]["byte_start"]
            )
        else:
            end = len(raw)

        sections.append(
            {
                "ordinal": ordinal,
                "title": str(heading["title"]),
                "heading_level": int(
                    heading["heading_level"]
                ),
                "byte_start": start,
                "byte_end": end,
                "line_start": line_number(
                    starts,
                    start,
                ),
                "line_end": ending_line_number(
                    starts,
                    end,
                ),
            }
        )
        ordinal += 1

    return sections


def build_chunks(
    raw: bytes,
    sections: list[dict[str, object]],
    chunk_bytes: int,
) -> list[dict[str, object]]:
    if chunk_bytes < 8:
        raise ValidationError(
            "chunk_bytes must be at least 8"
        )

    starts = line_starts(raw)
    chunks: list[dict[str, object]] = []
    ordinal = 0

    for section in sections:
        section_start = int(
            section["byte_start"]
        )
        section_end = int(
            section["byte_end"]
        )
        cursor = section_start

        while cursor < section_end:
            desired_end = min(
                cursor + chunk_bytes,
                section_end,
            )

            if desired_end < section_end:
                newline = raw.rfind(
                    b"\n",
                    cursor,
                    desired_end,
                )

                if newline >= cursor:
                    end = newline + 1
                else:
                    end = safe_utf8_end(
                        raw,
                        cursor,
                        desired_end,
                    )
            else:
                end = section_end

            if end <= cursor:
                raise ValidationError(
                    "chunker failed to advance"
                )

            chunk_raw = raw[cursor:end]
            chunk_text = chunk_raw.decode(
                "utf-8",
                errors="strict",
            )

            chunks.append(
                {
                    "ordinal": ordinal,
                    "section_ordinal": int(
                        section["ordinal"]
                    ),
                    "sha256": hashlib.sha256(
                        chunk_raw
                    ).hexdigest(),
                    "byte_start": cursor,
                    "byte_end": end,
                    "line_start": line_number(
                        starts,
                        cursor,
                    ),
                    "line_end": ending_line_number(
                        starts,
                        end,
                    ),
                    "content": chunk_text,
                }
            )

            ordinal += 1
            cursor = end

    return chunks


def verify_chunk_coverage(
    raw: bytes,
    chunks: list[dict[str, object]],
) -> dict[str, object]:
    if not chunks:
        raise ValidationError(
            "document produced no chunks"
        )

    expected_start = 0
    reconstructed = bytearray()

    for expected_ordinal, chunk in enumerate(chunks):
        ordinal = int(chunk["ordinal"])
        start = int(chunk["byte_start"])
        end = int(chunk["byte_end"])

        if ordinal != expected_ordinal:
            raise ValidationError(
                "chunk ordinals are not contiguous"
            )

        if start != expected_start:
            raise ValidationError(
                "chunk coverage contains a gap "
                "or overlap"
            )

        content_bytes = str(
            chunk["content"]
        ).encode("utf-8")

        if len(content_bytes) != end - start:
            raise ValidationError(
                "chunk byte coordinates do not "
                "match content"
            )

        digest = hashlib.sha256(
            content_bytes
        ).hexdigest()

        if digest != str(chunk["sha256"]):
            raise ValidationError(
                "chunk checksum mismatch"
            )

        reconstructed.extend(content_bytes)
        expected_start = end

    if expected_start != len(raw):
        raise ValidationError(
            "chunk coverage does not reach "
            "document end"
        )

    reconstructed_bytes = bytes(reconstructed)

    if reconstructed_bytes != raw:
        raise ValidationError(
            "chunk reconstruction differs "
            "from source bytes"
        )

    return {
        "valid": True,
        "covered_bytes": len(
            reconstructed_bytes
        ),
        "chunk_count": len(chunks),
        "coverage_sha256": hashlib.sha256(
            reconstructed_bytes
        ).hexdigest(),
    }
