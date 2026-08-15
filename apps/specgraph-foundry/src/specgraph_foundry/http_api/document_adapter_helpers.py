"""Shared helpers for the document adapters.

XML safety, paragraph text, and the derivation metadata every adapter attaches.
The XML guard in particular belongs somewhere findable: it is what stops a
crafted document from expanding into an entity bomb.
"""

from __future__ import annotations

from .document_security import BLOCKED_HTML_PARENTS
from .document_security import DocumentSecurityLimits
from importlib import metadata as importlib_metadata
import zipfile
from io import BytesIO
from xml.etree import ElementTree
from dataclasses import dataclass
from html.parser import HTMLParser
from .document_security import (
    BLOCK_BOUNDARY_TAGS,
    DocumentLimitExceededError,
    InvalidDocumentError,
)

@dataclass(frozen=True)
class DocumentDerivation:
    adapter_name: str
    adapter_version: str
    detected_media_type: str
    derived_text: str
    metadata: dict[str, object]

    @property
    def derived_bytes(self) -> bytes:
        return self.derived_text.encode("utf-8")


class HtmlTextExtractor(HTMLParser):
    def __init__(
        self,
        limits: DocumentSecurityLimits,
    ) -> None:
        super().__init__(convert_charrefs=True)
        self.limits = limits
        self.depth = 0
        self.token_count = 0
        self.stack: list[str] = []
        self.parts: list[str] = []

    def _bump_token(self) -> None:
        self.token_count += 1
        if self.token_count > self.limits.max_html_tokens:
            raise DocumentLimitExceededError(
                "HTML document exceeds the configured token limit"
            )

    def _is_blocked(self) -> bool:
        return any(
            tag in BLOCKED_HTML_PARENTS
            for tag in self.stack
        )

    def _append_break(self) -> None:
        if not self.parts:
            return
        if self.parts[-1].endswith("\n"):
            return
        self.parts.append("\n")

    def handle_starttag(
        self,
        tag: str,
        attrs,
    ) -> None:
        del attrs
        self._bump_token()
        self.depth += 1
        if self.depth > self.limits.max_html_nesting:
            raise DocumentLimitExceededError(
                "HTML document exceeds the configured nesting limit"
            )
        normalized = tag.casefold()
        self.stack.append(normalized)
        if normalized in BLOCK_BOUNDARY_TAGS:
            self._append_break()

    def handle_endtag(
        self,
        tag: str,
    ) -> None:
        self._bump_token()
        normalized = tag.casefold()
        if self.stack:
            self.stack.pop()
        self.depth = max(0, self.depth - 1)
        if normalized in BLOCK_BOUNDARY_TAGS:
            self._append_break()

    def handle_startendtag(
        self,
        tag: str,
        attrs,
    ) -> None:
        self.handle_starttag(tag, attrs)
        self.handle_endtag(tag)

    def handle_data(
        self,
        data: str,
    ) -> None:
        self._bump_token()
        if self._is_blocked():
            return
        if not data:
            return
        self.parts.append(data)

    def handle_entityref(
        self,
        name: str,
    ) -> None:
        self._bump_token()
        super().handle_entityref(name)

    def handle_charref(
        self,
        name: str,
    ) -> None:
        self._bump_token()
        super().handle_charref(name)

    def text(self) -> str:
        lines = [
            line.strip()
            for line in "".join(self.parts).splitlines()
        ]
        filtered = [
            line
            for line in lines
            if line
        ]
        return "\n".join(filtered)

WORD_NAMESPACE = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
REL_NAMESPACE = "{http://schemas.openxmlformats.org/package/2006/relationships}"
WORKSPACE_PREVIEW_LIMIT = 5
DOCX_BLOCK_SEPARATOR = "\n\n"
PDF_PAGE_SEPARATOR = "\n\n"
XML_FORBIDDEN_MARKERS = (
    b"<!DOCTYPE",
    b"<!ENTITY",
)
DOCX_FORBIDDEN_PREFIXES = (
    "word/activex/",
    "word/controls/",
    "word/embeddings/",
    "word/vbaproject.bin",
)

def assert_safe_xml(payload: bytes) -> None:
    upper = payload.upper()
    if any(marker in upper for marker in XML_FORBIDDEN_MARKERS):
        raise InvalidDocumentError(
            "document contains unsupported XML declarations"
        )


def xml_root(payload: bytes):
    try:
        return ElementTree.fromstring(payload)
    except ElementTree.ParseError as error:
        raise InvalidDocumentError(
            "document contains invalid XML"
        ) from error


def docx_paragraph_text(paragraph) -> str:
    fragments: list[str] = []

    for node in paragraph.iter():
        if node.tag == f"{WORD_NAMESPACE}t":
            fragments.append(node.text or "")
        elif node.tag == f"{WORD_NAMESPACE}tab":
            fragments.append("\t")
        elif node.tag in {
            f"{WORD_NAMESPACE}br",
            f"{WORD_NAMESPACE}cr",
        }:
            fragments.append("\n")

    return "".join(fragments).strip()


def metadata(
    *,
    locator_kind: str,
    locators: list[dict[str, object]],
    page_count: int | None = None,
) -> dict[str, object]:
    preview = locators[:WORKSPACE_PREVIEW_LIMIT]
    metadata: dict[str, object] = {
        "locator_kind": locator_kind,
        "locator_count": len(locators),
        "locators_preview": preview,
        "locators_has_more": len(locators) > len(preview),
    }

    if page_count is not None:
        metadata["page_count"] = page_count

    return metadata


def dependency_version(
    name: str,
    fallback: str,
) -> str:
    try:
        return importlib_metadata.version(name)
    except importlib_metadata.PackageNotFoundError:
        return fallback
