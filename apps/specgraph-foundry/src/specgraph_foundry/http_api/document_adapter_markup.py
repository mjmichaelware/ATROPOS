"""Extracting text from plain text, markdown and HTML.

The formats where the bytes are already text and the work is deciding what
counts as structure.
"""

from __future__ import annotations

from .document_adapter_helpers import DOCX_BLOCK_SEPARATOR
from .document_adapter_helpers import DocumentDerivation
from .document_adapter_helpers import HtmlTextExtractor
from .document_adapter_helpers import metadata
from .document_adapter_helpers import *  # noqa: F401,F403
import zipfile
from io import BytesIO
from .document_security import DocumentLimitExceededError
from .document_security import InvalidDocumentError
from .document_security import NoExtractableTextError
from .document_security import decode_strict_utf8
from .document_security import normalize_text_newlines
from .document_security import validate_json_text
from .document_security import validate_text_output
import json


def adapt_text_family(
    registry,
    *,
    raw: bytes,
    declared_media_type: str,
) -> DocumentDerivation:
    text = normalize_text_newlines(
        decode_strict_utf8(raw)
    )

    if declared_media_type == "application/json":
        validate_json_text(text, registry.limits)

    validate_text_output(text, registry.limits)

    return DocumentDerivation(
        adapter_name="utf8_text",
        adapter_version="1",
        detected_media_type=declared_media_type,
        derived_text=text,
        metadata=metadata(
            locator_kind="document",
            locators=[
                {
                    "kind": "document",
                    "ordinal": 1,
                    "label": "Document",
                }
            ],
        ),
    )


def adapt_html(
    registry,
    raw: bytes,
) -> DocumentDerivation:
    text = decode_strict_utf8(raw)
    extractor = HtmlTextExtractor(registry.limits)

    try:
        extractor.feed(text)
        extractor.close()
    except DocumentLimitExceededError:
        raise
    except Exception as error:
        raise InvalidDocumentError(
            "HTML document is invalid"
        ) from error

    derived = normalize_text_newlines(
        extractor.text()
    )
    validate_text_output(derived, registry.limits)

    if not derived.strip():
        raise NoExtractableTextError(
            "HTML document contains no extractable text"
        )

    return DocumentDerivation(
        adapter_name="html_sanitizer",
        adapter_version="1",
        detected_media_type="text/html",
        derived_text=derived,
        metadata=metadata(
            locator_kind="block",
            locators=[
                {
                    "kind": "document",
                    "ordinal": 1,
                    "label": "Sanitized HTML",
                }
            ],
        ),
    )


def serialize_blocks(
    registry,
    blocks: list[dict[str, object]],
) -> tuple[str, list[dict[str, object]]]:
    parts: list[str] = []
    locators: list[dict[str, object]] = []
    byte_cursor = 0
    line_cursor = 1

    for index, block in enumerate(blocks, start=1):
        text = str(block["text"]).strip()
        if not text:
            continue

        if parts:
            parts.append(DOCX_BLOCK_SEPARATOR)
            byte_cursor += len(
                DOCX_BLOCK_SEPARATOR.encode("utf-8")
            )
            line_cursor += DOCX_BLOCK_SEPARATOR.count("\n")

        byte_start = byte_cursor
        line_start = line_cursor
        encoded = text.encode("utf-8")
        byte_end = byte_start + len(encoded)
        line_end = line_start + text.count("\n")

        locators.append(
            {
                "kind": str(
                    block.get("kind", "block")
                ),
                "ordinal": int(
                    block.get("ordinal", index)
                ),
                "label": str(
                    block.get("label", f"Block {index}")
                ),
                "derived_byte_start": byte_start,
                "derived_byte_end": byte_end,
                "derived_line_start": line_start,
                "derived_line_end": line_end,
            }
        )

        parts.append(text)
        byte_cursor = byte_end
        line_cursor = line_end

    return "".join(parts), locators
