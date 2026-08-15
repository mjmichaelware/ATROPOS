from __future__ import annotations

from .document_adapter_helpers import DocumentDerivation
from .document_adapter_helpers import *  # noqa: F401,F403 - re-exported  # noqa: F401,F403
import zipfile
from io import BytesIO
from .document_adapter_docx import adapt_docx, docx_table_text
from .document_adapter_pdf import adapt_pdf
from .document_adapter_markup import adapt_html, adapt_text_family, serialize_blocks

import json
import zipfile
from dataclasses import dataclass
from html.parser import HTMLParser
from importlib import metadata
from io import BytesIO
from xml.etree import ElementTree

from .document_security import (
    BLOCK_BOUNDARY_TAGS,
    BLOCKED_HTML_PARENTS,
    DOCX_MIME_TYPE,
    HTML_MIME_TYPES,
    PDF_MIME_TYPE,
    YAML_MIME_TYPES,
    DependencyUnavailableError,
    DocumentEncryptedError,
    DocumentLimitExceededError,
    DocumentSecurityLimits,
    InvalidDocumentError,
    NoExtractableTextError,
    assert_extension_matches_media_type,
    assert_supported_declared_media_type,
    decode_strict_utf8,
    media_type_family,
    normalize_text_newlines,
    validate_json_text,
    validate_text_output,
)








class DocumentAdapterRegistry:
    def __init__(
        self,
        limits: DocumentSecurityLimits,
    ) -> None:
        self.limits = limits

    def derive_text(
        self,
        *,
        filename: str,
        declared_media_type: str,
        storage_media_type: str | None,
        raw: bytes,
    ) -> DocumentDerivation:
        assert_supported_declared_media_type(
            declared_media_type
        )
        assert_extension_matches_media_type(
            filename,
            declared_media_type,
        )

        family = media_type_family(
            declared_media_type
        )
        storage_family = (
            media_type_family(storage_media_type)
            if storage_media_type
            else None
        )

        if (
            storage_family is not None
            and storage_family != family
            and not (
                family == "text"
                and storage_family == "markdown"
            )
        ):
            raise InvalidDocumentError(
                "uploaded source media type is invalid"
            )

        if family in {
            "json",
            "markdown",
            "text",
            "yaml",
        }:
            return self._adapt_text_family(
                raw=raw,
                declared_media_type=declared_media_type,
            )

        if family == "html":
            return self._adapt_html(raw)

        if family == "docx":
            return self._adapt_docx(raw)

        if family == "pdf":
            return self._adapt_pdf(raw)

        raise InvalidDocumentError(
            "media type is not supported"
        )

    def _adapt_text_family(
        self,
        *,
        raw: bytes,
        declared_media_type: str,
    ) -> DocumentDerivation:
        """Delegates to :func:`document_adapter_markup.adapt_text_family`."""
        return adapt_text_family(
            self,
            raw=raw,
            declared_media_type=declared_media_type,
        )


    def _adapt_html(
        self,
        raw: bytes,
    ) -> DocumentDerivation:
        """Delegates to :func:`document_adapter_markup.adapt_html`."""
        return adapt_html(
            self,
            raw,
        )


    def _adapt_docx(
        self,
        raw: bytes,
    ) -> DocumentDerivation:
        """Delegates to :func:`document_adapter_docx.adapt_docx`."""
        return adapt_docx(
            self,
            raw,
        )


    def _adapt_pdf(
        self,
        raw: bytes,
    ) -> DocumentDerivation:
        """Delegates to :func:`document_adapter_pdf.adapt_pdf`."""
        return adapt_pdf(
            self,
            raw,
        )





    def _docx_table_text(self, table) -> str:
        """Delegates to :func:`document_adapter_docx.docx_table_text`."""
        return docx_table_text(
            self,
            table,
        )


    def _serialize_blocks(
        self,
        blocks: list[dict[str, object]],
    ) -> tuple[str, list[dict[str, object]]]:
        """Delegates to :func:`document_adapter_markup.serialize_blocks`."""
        return serialize_blocks(
            self,
            blocks,
        )




