"""Extracting text from a PDF.

Separate because it is the adapter that can legitimately find nothing -- a
scanned page has no text layer -- and that outcome must be reported rather than
treated as an empty document.
"""

from __future__ import annotations

from .document_adapter_helpers import DocumentDerivation
from .document_adapter_helpers import metadata
from .document_adapter_helpers import *  # noqa: F401,F403
from .document_adapter_helpers import dependency_version
import zipfile
from io import BytesIO
from .document_adapter_markup import serialize_blocks
from .document_security import DependencyUnavailableError
from .document_security import DocumentEncryptedError
from .document_security import DocumentLimitExceededError
from .document_security import InvalidDocumentError
from .document_security import NoExtractableTextError
from .document_security import PDF_MIME_TYPE
from .document_security import normalize_text_newlines
from .document_security import validate_text_output
import json


def adapt_pdf(
    registry,
    raw: bytes,
) -> DocumentDerivation:
    if not raw.startswith(b"%PDF-"):
        raise InvalidDocumentError("PDF header is invalid")

    try:
        from pypdf import PdfReader
        from pypdf.errors import PdfReadError
    except Exception as error:
        raise DependencyUnavailableError(
            "PDF adapter dependency is unavailable"
        ) from error

    try:
        reader = PdfReader(
            BytesIO(raw),
            strict=True,
        )
    except Exception as error:
        raise InvalidDocumentError(
            "PDF document is invalid"
        ) from error

    if reader.is_encrypted:
        raise DocumentEncryptedError(
            "PDF document is encrypted"
        )

    page_count = len(reader.pages)
    if page_count == 0:
        raise InvalidDocumentError(
            "PDF document is empty"
        )
    if page_count > registry.limits.max_pdf_pages:
        raise DocumentLimitExceededError(
            "PDF document exceeds the configured page limit"
        )

    root = reader.trailer.get("/Root", {})
    root_keys = (
        set(root.keys())
        if hasattr(root, "keys")
        else set()
    )
    if any(
        key in root_keys
        for key in (
            "/AcroForm",
            "/AA",
            "/OpenAction",
        )
    ):
        raise InvalidDocumentError(
            "PDF document contains unsupported active content"
        )

    page_blocks: list[dict[str, object]] = []

    for index, page in enumerate(reader.pages, start=1):
        try:
            text = page.extract_text() or ""
        except Exception as error:
            raise InvalidDocumentError(
                "PDF text extraction failed"
            ) from error

        normalized = normalize_text_newlines(
            text
        ).strip()
        if not normalized:
            continue

        page_blocks.append(
            {
                "kind": "page",
                "label": f"Page {index}",
                "ordinal": index,
                "text": normalized,
            }
        )

    if not page_blocks:
        raise NoExtractableTextError(
            "PDF document contains no extractable text"
        )

    derived_text, locators = serialize_blocks(registry, 
        page_blocks
    )
    validate_text_output(derived_text, registry.limits)

    return DocumentDerivation(
        adapter_name="pypdf_text",
        adapter_version=dependency_version(
            "pypdf",
            fallback="local-compat-1",
        ),
        detected_media_type=PDF_MIME_TYPE,
        derived_text=derived_text,
        metadata=metadata(
            locator_kind="page",
            locators=locators,
            page_count=page_count,
        ),
    )
