from __future__ import annotations

import re
from io import BytesIO
from typing import BinaryIO

from .errors import PdfReadError


class _Trailer:
    def get(self, key: str, default=None):
        if key == "/Root":
            return {}
        return default


class Page:
    def __init__(self, text: str):
        self._text = text

    def extract_text(self) -> str:
        return self._text


class PdfReader:
    def __init__(self, stream: BinaryIO | BytesIO, strict: bool = True):
        raw = stream.read()
        if not isinstance(raw, bytes):
            raise PdfReadError("PDF stream must return bytes")
        if not raw.startswith(b"%PDF-"):
            raise PdfReadError("PDF header is invalid")
        self._raw = raw
        self.is_encrypted = b"/Encrypt" in raw or b"%%SPECGRAPH_ENCRYPTED" in raw
        self.trailer = _Trailer()
        self.pages = [
            Page(text)
            for text in _extract_fixture_text(raw)
        ]


class PdfWriter:
    def __init__(self):
        self._source = b"%PDF-1.4\n"
        self._encrypted = False

    def append(self, stream: BinaryIO | BytesIO) -> None:
        raw = stream.read()
        if not isinstance(raw, bytes) or not raw.startswith(b"%PDF-"):
            raise PdfReadError("PDF header is invalid")
        self._source = raw

    def encrypt(self, password: str) -> None:
        if not password:
            raise ValueError("password is required")
        self._encrypted = True

    def write(self, stream: BinaryIO | BytesIO) -> None:
        payload = self._source
        if self._encrypted:
            payload = (
                payload
                + b"\n%%SPECGRAPH_ENCRYPTED\n"
                + b"trailer\n<< /Encrypt <<>> >>\n"
            )
        stream.write(payload)


def _extract_fixture_text(raw: bytes) -> list[str]:
    texts: list[str] = []
    for match in re.finditer(
        rb"stream\s*(.*?)\s*endstream",
        raw,
        re.DOTALL,
    ):
        stream = match.group(1)
        text_match = re.search(
            rb"\((.*?)\)\s*Tj",
            stream,
            re.DOTALL,
        )
        if text_match is None:
            texts.append("")
            continue
        encoded = text_match.group(1)
        text = _decode_pdf_literal(encoded)
        texts.append(text)
    return texts


def _decode_pdf_literal(value: bytes) -> str:
    output = bytearray()
    index = 0
    while index < len(value):
        char = value[index]
        if char == 0x5C and index + 1 < len(value):
            index += 1
            output.append(value[index])
        else:
            output.append(char)
        index += 1
    return output.decode("utf-8", errors="replace")
