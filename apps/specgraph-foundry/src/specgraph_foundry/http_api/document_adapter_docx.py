"""Extracting text from a .docx.

The largest adapter, and the only one that walks a zip container and an XML
tree to find paragraphs and tables in reading order.
"""

from __future__ import annotations

from .document_adapter_helpers import DOCX_FORBIDDEN_PREFIXES
from .document_adapter_helpers import DocumentDerivation
from .document_adapter_helpers import REL_NAMESPACE
from .document_adapter_helpers import WORD_NAMESPACE
from .document_adapter_helpers import assert_safe_xml
from .document_adapter_helpers import docx_paragraph_text
from .document_adapter_helpers import metadata
from .document_adapter_helpers import xml_root
from .document_adapter_helpers import *  # noqa: F401,F403
import zipfile
from io import BytesIO
from .document_adapter_markup import serialize_blocks
from .document_security import DOCX_MIME_TYPE
from .document_security import DocumentEncryptedError
from .document_security import DocumentLimitExceededError
from .document_security import InvalidDocumentError
from .document_security import NoExtractableTextError
from .document_security import validate_text_output
import json


def adapt_docx(
    registry,
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
                    > registry.limits.max_docx_archive_members
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
                        > registry.limits.max_archive_compression_ratio
                    ):
                        raise DocumentLimitExceededError(
                            "DOCX archive exceeds the configured compression ratio"
                        )

                expanded_bytes += info.file_size
                if (
                    expanded_bytes
                    > registry.limits.max_docx_expanded_bytes
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
                assert_safe_xml(payload)

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
        rel_root = xml_root(payload)
        for relation in rel_root.findall(
            f"{REL_NAMESPACE}Relationship"
        ):
            if relation.attrib.get("TargetMode") == "External":
                raise InvalidDocumentError(
                    "DOCX archive contains external relationships"
                )

    root = xml_root(document_xml)
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
            text = docx_paragraph_text(child)
            if text:
                blocks.append(
                    {
                        "kind": "paragraph",
                        "label": f"Paragraph {len(blocks) + 1}",
                        "text": text,
                    }
                )
        elif child.tag == f"{WORD_NAMESPACE}tbl":
            text = docx_table_text(registry, child)
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

    derived_text, locators = serialize_blocks(registry, blocks)
    validate_text_output(derived_text, registry.limits)

    return DocumentDerivation(
        adapter_name="docx_xml",
        adapter_version="1",
        detected_media_type=DOCX_MIME_TYPE,
        derived_text=derived_text,
        metadata=metadata(
            locator_kind="block",
            locators=locators,
        ),
    )


def docx_table_text(self, table) -> str:
    row_values: list[str] = []

    for row in table.findall(f"{WORD_NAMESPACE}tr"):
        cell_values: list[str] = []
        for cell in row.findall(f"{WORD_NAMESPACE}tc"):
            paragraphs = [
                docx_paragraph_text(paragraph)
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
