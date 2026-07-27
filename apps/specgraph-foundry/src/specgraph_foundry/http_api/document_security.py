from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import PurePosixPath


CONTROL_EXCEPTIONS = {
    0x09,
    0x0A,
    0x0D,
}

BLOCKED_HTML_PARENTS = {
    "embed",
    "iframe",
    "noscript",
    "object",
    "script",
    "style",
    "svg",
    "template",
}

BLOCK_BOUNDARY_TAGS = {
    "article",
    "aside",
    "blockquote",
    "br",
    "div",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "header",
    "hr",
    "li",
    "main",
    "nav",
    "ol",
    "p",
    "pre",
    "section",
    "table",
    "td",
    "th",
    "tr",
    "ul",
}

TEXT_MIME_TYPES = {
    "application/json",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/xhtml+xml",
    "application/x-yaml",
    "application/yaml",
    "text/html",
    "text/markdown",
    "text/plain",
    "text/x-c",
    "text/x-c++src",
    "text/x-go",
    "text/x-java-source",
    "text/x-python",
    "text/x-rustsrc",
    "text/yaml",
}

PDF_MIME_TYPE = "application/pdf"
DOCX_MIME_TYPE = (
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
)
YAML_MIME_TYPES = {
    "application/x-yaml",
    "application/yaml",
    "text/yaml",
}
HTML_MIME_TYPES = {
    "application/xhtml+xml",
    "text/html",
}

FILENAME_CONTROL_PATTERN = re.compile(r"[\x00-\x1f]")
LOWERCASE_SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


class InvalidDocumentError(RuntimeError):
    pass


class DocumentEncryptedError(RuntimeError):
    pass


class DocumentLimitExceededError(RuntimeError):
    pass


class NoExtractableTextError(RuntimeError):
    pass


class DependencyUnavailableError(RuntimeError):
    pass


@dataclass(frozen=True)
class DocumentSecurityLimits:
    max_original_bytes: int
    max_extracted_text_bytes: int = 4 * 1024 * 1024
    max_pdf_pages: int = 200
    max_docx_archive_members: int = 256
    max_docx_expanded_bytes: int = 32 * 1024 * 1024
    max_archive_compression_ratio: int = 200
    max_json_nesting: int = 64
    max_html_tokens: int = 20_000
    max_html_nesting: int = 128

    def __post_init__(self) -> None:
        if self.max_original_bytes < 1:
            raise ValueError(
                "maximum original bytes must be positive"
            )
        if (
            self.max_extracted_text_bytes < 1
            or self.max_extracted_text_bytes
            > 16 * 1024 * 1024
        ):
            raise ValueError(
                "maximum extracted text bytes is invalid"
            )
        if self.max_pdf_pages < 1 or self.max_pdf_pages > 2000:
            raise ValueError("maximum PDF pages is invalid")
        if (
            self.max_docx_archive_members < 1
            or self.max_docx_archive_members > 4096
        ):
            raise ValueError(
                "maximum DOCX archive members is invalid"
            )
        if (
            self.max_docx_expanded_bytes < 1
            or self.max_docx_expanded_bytes
            > 128 * 1024 * 1024
        ):
            raise ValueError(
                "maximum DOCX expanded bytes is invalid"
            )
        if (
            self.max_archive_compression_ratio < 1
            or self.max_archive_compression_ratio > 1000
        ):
            raise ValueError(
                "maximum archive compression ratio is invalid"
            )
        if (
            self.max_json_nesting < 1
            or self.max_json_nesting > 512
        ):
            raise ValueError("maximum JSON nesting is invalid")
        if (
            self.max_html_tokens < 1
            or self.max_html_tokens > 200_000
        ):
            raise ValueError("maximum HTML tokens is invalid")
        if (
            self.max_html_nesting < 1
            or self.max_html_nesting > 2048
        ):
            raise ValueError("maximum HTML nesting is invalid")


def normalized_filename(filename: str) -> str:
    candidate = filename.strip()

    if not candidate:
        raise InvalidDocumentError("filename is required")
    if len(candidate) > 255:
        raise InvalidDocumentError("filename is invalid")
    if candidate in {".", ".."}:
        raise InvalidDocumentError("filename is invalid")
    if FILENAME_CONTROL_PATTERN.search(candidate):
        raise InvalidDocumentError("filename is invalid")
    if "/" in candidate or "\\" in candidate:
        raise InvalidDocumentError("filename is invalid")

    return candidate


def filename_extension(filename: str) -> str | None:
    normalized = normalized_filename(filename)
    suffix = PurePosixPath(normalized).suffix.casefold()
    return suffix or None


def assert_supported_declared_media_type(media_type: str) -> None:
    normalized = media_type.strip().casefold()

    if normalized == PDF_MIME_TYPE or normalized in TEXT_MIME_TYPES:
        return

    raise InvalidDocumentError("media type is not supported")


def validate_sha256(value: str) -> str:
    normalized = value.strip()
    if not LOWERCASE_SHA256_PATTERN.fullmatch(normalized):
        raise InvalidDocumentError(
            "sha256 must be a lowercase hexadecimal digest"
        )
    return normalized


def ensure_safe_text_bytes(raw: bytes) -> None:
    if b"\x00" in raw:
        raise InvalidDocumentError("document contains binary content")

    for byte in raw:
        if byte < 0x20 and byte not in CONTROL_EXCEPTIONS:
            raise InvalidDocumentError("document contains binary content")


def decode_strict_utf8(raw: bytes) -> str:
    ensure_safe_text_bytes(raw)

    try:
        return raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        raise InvalidDocumentError(
            "document must be valid UTF-8 text"
        ) from error


def normalize_text_newlines(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n")


def validate_text_output(
    text: str,
    limits: DocumentSecurityLimits,
) -> bytes:
    encoded = normalize_text_newlines(text).encode("utf-8")

    if len(encoded) > limits.max_extracted_text_bytes:
        raise DocumentLimitExceededError(
            "derived text exceeds the configured maximum"
        )

    return encoded


def json_nesting_depth(value: object) -> int:
    if isinstance(value, dict):
        if not value:
            return 1
        return 1 + max(
            json_nesting_depth(item)
            for item in value.values()
        )

    if isinstance(value, list):
        if not value:
            return 1
        return 1 + max(
            json_nesting_depth(item)
            for item in value
        )

    return 1


def validate_json_text(
    text: str,
    limits: DocumentSecurityLimits,
) -> None:
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError as error:
        raise InvalidDocumentError("JSON document is invalid") from error

    if json_nesting_depth(parsed) > limits.max_json_nesting:
        raise DocumentLimitExceededError(
            "JSON document exceeds the configured nesting limit"
        )


def extension_family(extension: str | None) -> str | None:
    if extension in {".txt", ".text"}:
        return "text"
    if extension in {".md", ".markdown"}:
        return "markdown"
    if extension == ".json":
        return "json"
    if extension in {".yaml", ".yml"}:
        return "yaml"
    if extension in {
        ".c",
        ".cc",
        ".cpp",
        ".go",
        ".java",
        ".js",
        ".py",
        ".rs",
        ".ts",
    }:
        return "source"
    if extension in {".html", ".htm", ".xhtml"}:
        return "html"
    if extension == ".docx":
        return "docx"
    if extension == ".pdf":
        return "pdf"
    return None


def media_type_family(media_type: str) -> str:
    normalized = media_type.casefold()

    if normalized == PDF_MIME_TYPE:
        return "pdf"
    if normalized == DOCX_MIME_TYPE:
        return "docx"
    if normalized in HTML_MIME_TYPES:
        return "html"
    if normalized == "application/json":
        return "json"
    if normalized in YAML_MIME_TYPES:
        return "yaml"
    if normalized == "text/markdown":
        return "markdown"
    if normalized.startswith("text/"):
        if normalized in {"text/html", "text/xml"}:
            return "html"
        return "text"
    return "text"


def assert_extension_matches_media_type(
    filename: str,
    media_type: str,
) -> None:
    extension = filename_extension(filename)

    if extension is None:
        return

    extension_kind = extension_family(extension)
    if extension_kind is None:
        return

    media_kind = media_type_family(media_type)

    if extension_kind == "source" and media_kind == "text":
        return
    if extension_kind == "yaml" and media_kind == "text":
        return
    if extension_kind == "text" and media_kind in {"text", "markdown"}:
        return
    if extension_kind == media_kind:
        return

    raise InvalidDocumentError(
        "filename extension does not match media type"
    )
