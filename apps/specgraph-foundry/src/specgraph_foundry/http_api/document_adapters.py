from __future__ import annotations

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
        text = normalize_text_newlines(
            decode_strict_utf8(raw)
        )

        if declared_media_type == "application/json":
            validate_json_text(text, self.limits)

        validate_text_output(text, self.limits)

        return DocumentDerivation(
            adapter_name="utf8_text",
            adapter_version="1",
            detected_media_type=declared_media_type,
            derived_text=text,
            metadata=self._metadata(
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

    def _adapt_html(
        self,
        raw: bytes,
    ) -> DocumentDerivation:
        text = decode_strict_utf8(raw)
        extractor = HtmlTextExtractor(self.limits)

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
        validate_text_output(derived, self.limits)

        if not derived.strip():
            raise NoExtractableTextError(
                "HTML document contains no extractable text"
            )

        return DocumentDerivation(
            adapter_name="html_sanitizer",
            adapter_version="1",
            detected_media_type="text/html",
            derived_text=derived,
            metadata=self._metadata(
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

    def _adapt_docx(
        self,
        raw: bytes,
    ) -> DocumentDerivation:
        archive = BytesIO(raw)
        if not zipfile.is_zipfile(archive):
            raise InvalidDocumentError(
                "DOCX container is invalid"
            )

        try:
            with zipfile.ZipFile(archive) as package:
                names_seen: set[str] = set()
                expanded_bytes = 0
                document_xml: bytes | None = None
                rels_payloads: list[bytes] = []
                member_count = 0

                for info in package.infolist():
                    member_count += 1
                    if (
                        member_count
                        > self.limits.max_docx_archive_members
                    ):
                        raise DocumentLimitExceededError(
                            "DOCX archive exceeds the configured member limit"
                        )

                    normalized_name = info.filename.replace("\\", "/").lstrip("/")
                    lower_name = normalized_name.casefold()

                    if not normalized_name or "/../" in f"/{normalized_name}/":
                        raise InvalidDocumentError(
                            "DOCX archive contains an invalid member path"
                        )
                    if lower_name in names_seen:
                        raise InvalidDocumentError(
                            "DOCX archive contains duplicate members"
                        )
                    names_seen.add(lower_name)

                    if (
                        info.external_attr >> 16
                    ) & 0o170000 == 0o120000:
                        raise InvalidDocumentError(
                            "DOCX archive contains an unsupported symlink"
                        )

                    if info.flag_bits & 0x1:
                        raise DocumentEncryptedError(
                            "DOCX archive is encrypted"
                        )

                    if info.compress_size > 0:
                        ratio = info.file_size / info.compress_size
                        if (
                            ratio
                            > self.limits.max_archive_compression_ratio
                        ):
                            raise DocumentLimitExceededError(
                                "DOCX archive exceeds the configured compression ratio"
                            )

                    expanded_bytes += info.file_size
                    if (
                        expanded_bytes
                        > self.limits.max_docx_expanded_bytes
                    ):
                        raise DocumentLimitExceededError(
                            "DOCX archive exceeds the configured expanded size limit"
                        )

                    if lower_name.startswith(
                        DOCX_FORBIDDEN_PREFIXES
                    ) or any(
                        lower_name.startswith(prefix)
                        for prefix in DOCX_FORBIDDEN_PREFIXES
                    ):
                        raise InvalidDocumentError(
                            "DOCX archive contains unsupported active content"
                        )

                    payload = package.read(info)
                    self._assert_safe_xml(payload)

                    if lower_name == "word/document.xml":
                        document_xml = payload
                    if lower_name.endswith(".rels"):
                        rels_payloads.append(payload)
        except zipfile.BadZipFile as error:
            raise InvalidDocumentError(
                "DOCX archive is invalid"
            ) from error

        if document_xml is None:
            raise InvalidDocumentError(
                "DOCX archive is missing word/document.xml"
            )

        for payload in rels_payloads:
            rel_root = self._xml_root(payload)
            for relation in rel_root.findall(
                f"{REL_NAMESPACE}Relationship"
            ):
                if relation.attrib.get("TargetMode") == "External":
                    raise InvalidDocumentError(
                        "DOCX archive contains external relationships"
                    )

        root = self._xml_root(document_xml)
        if any(
            element.tag.endswith("AlternateContent")
            for element in root.iter()
        ):
            raise InvalidDocumentError(
                "DOCX archive contains unsupported alternate content"
            )

        body = root.find(f".//{WORD_NAMESPACE}body")
        if body is None:
            raise InvalidDocumentError(
                "DOCX archive is missing a document body"
            )

        blocks: list[dict[str, object]] = []

        for child in list(body):
            if child.tag == f"{WORD_NAMESPACE}p":
                text = self._docx_paragraph_text(child)
                if text:
                    blocks.append(
                        {
                            "kind": "paragraph",
                            "label": f"Paragraph {len(blocks) + 1}",
                            "text": text,
                        }
                    )
            elif child.tag == f"{WORD_NAMESPACE}tbl":
                text = self._docx_table_text(child)
                if text:
                    blocks.append(
                        {
                            "kind": "table",
                            "label": f"Table {len(blocks) + 1}",
                            "text": text,
                        }
                    )

        if not blocks:
            raise NoExtractableTextError(
                "DOCX archive contains no extractable text"
            )

        derived_text, locators = self._serialize_blocks(blocks)
        validate_text_output(derived_text, self.limits)

        return DocumentDerivation(
            adapter_name="docx_xml",
            adapter_version="1",
            detected_media_type=DOCX_MIME_TYPE,
            derived_text=derived_text,
            metadata=self._metadata(
                locator_kind="block",
                locators=locators,
            ),
        )

    def _adapt_pdf(
        self,
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
        if page_count > self.limits.max_pdf_pages:
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

        derived_text, locators = self._serialize_blocks(
            page_blocks
        )
        validate_text_output(derived_text, self.limits)

        return DocumentDerivation(
            adapter_name="pypdf_text",
            adapter_version=metadata.version("pypdf"),
            detected_media_type=PDF_MIME_TYPE,
            derived_text=derived_text,
            metadata=self._metadata(
                locator_kind="page",
                locators=locators,
                page_count=page_count,
            ),
        )

    @staticmethod
    def _assert_safe_xml(payload: bytes) -> None:
        upper = payload.upper()
        if any(marker in upper for marker in XML_FORBIDDEN_MARKERS):
            raise InvalidDocumentError(
                "document contains unsupported XML declarations"
            )

    @staticmethod
    def _xml_root(payload: bytes):
        try:
            return ElementTree.fromstring(payload)
        except ElementTree.ParseError as error:
            raise InvalidDocumentError(
                "document contains invalid XML"
            ) from error

    @staticmethod
    def _docx_paragraph_text(paragraph) -> str:
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

    def _docx_table_text(self, table) -> str:
        row_values: list[str] = []

        for row in table.findall(f"{WORD_NAMESPACE}tr"):
            cell_values: list[str] = []
            for cell in row.findall(f"{WORD_NAMESPACE}tc"):
                paragraphs = [
                    self._docx_paragraph_text(paragraph)
                    for paragraph in cell.findall(f"{WORD_NAMESPACE}p")
                ]
                content = "\n".join(
                    value
                    for value in paragraphs
                    if value
                ).strip()
                if content:
                    cell_values.append(content)

            if cell_values:
                row_values.append("\t".join(cell_values))

        return "\n".join(row_values).strip()

    def _serialize_blocks(
        self,
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

    @staticmethod
    def _metadata(
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
