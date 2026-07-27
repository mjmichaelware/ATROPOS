import hashlib
import sys
import unittest
from io import BytesIO
from unittest.mock import patch
from zipfile import ZIP_DEFLATED, ZipFile

from specgraph_foundry.http_api.document_adapters import (
    DocumentAdapterRegistry,
)
from specgraph_foundry.http_api.document_security import (
    DependencyUnavailableError,
    DocumentSecurityLimits,
    InvalidDocumentError,
    NoExtractableTextError,
)


def make_docx(
    *blocks: tuple[str, str],
) -> bytes:
    buffer = BytesIO()

    with ZipFile(
        buffer,
        "w",
        ZIP_DEFLATED,
    ) as archive:
        archive.writestr(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""",
        )
        archive.writestr(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""",
        )

        body_parts: list[str] = []
        for kind, text in blocks:
            if kind == "paragraph":
                body_parts.append(
                    "<w:p><w:r><w:t>"
                    + text
                    + "</w:t></w:r></w:p>"
                )
            elif kind == "table":
                body_parts.append(
                    "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>"
                    + text
                    + "</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
                )
            else:
                raise AssertionError(kind)

        archive.writestr(
            "word/document.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>"""
            + "".join(body_parts)
            + """</w:body>
</w:document>""",
        )

    return buffer.getvalue()


def make_pdf(
    *page_texts: str,
) -> bytes:
    objects: list[bytes] = []
    page_refs = [
        f"{index} 0 R".encode()
        for index in range(3, 3 + len(page_texts))
    ]
    objects.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    objects.append(
        b"<< /Type /Pages /Kids ["
        + b" ".join(page_refs)
        + b"] /Count "
        + str(len(page_texts)).encode()
        + b" >>"
    )

    next_object = 3 + len(page_texts)
    content_object_ids: list[int] = []
    font_object_id = next_object + len(page_texts)

    for _ in page_texts:
        content_object_ids.append(next_object)
        next_object += 1

    for page_index, _ in enumerate(page_texts):
        objects.append(
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] "
            + b"/Contents "
            + f"{content_object_ids[page_index]} 0 R".encode()
            + b" /Resources << /Font << /F1 "
            + f"{font_object_id} 0 R".encode()
            + b" >> >> >>"
        )

    for text in page_texts:
        stream = (
            "BT\n/F1 24 Tf\n72 72 Td\n("
            + text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
            + ") Tj\nET\n"
        ).encode("utf-8")
        objects.append(
            b"<< /Length "
            + str(len(stream)).encode()
            + b" >>\nstream\n"
            + stream
            + b"endstream"
        )

    objects.append(
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
    )

    output = bytearray(b"%PDF-1.4\n")
    offsets = [0]

    for object_id, payload in enumerate(objects, start=1):
        offsets.append(len(output))
        output.extend(f"{object_id} 0 obj\n".encode("utf-8"))
        output.extend(payload)
        output.extend(b"\nendobj\n")

    xref_offset = len(output)
    output.extend(f"xref\n0 {len(objects) + 1}\n".encode("utf-8"))
    output.extend(b"0000000000 65535 f \n")

    for offset in offsets[1:]:
        output.extend(f"{offset:010d} 00000 n \n".encode("utf-8"))

    output.extend(
        f"trailer\n<< /Root 1 0 R /Size {len(objects) + 1} >>\n".encode("utf-8")
    )
    output.extend(f"startxref\n{xref_offset}\n%%EOF\n".encode("utf-8"))
    return bytes(output)


class DocumentAdaptersTest(unittest.TestCase):
    def setUp(self) -> None:
        self.registry = DocumentAdapterRegistry(
            DocumentSecurityLimits(
                max_original_bytes=200_000
            )
        )

    def derive(
        self,
        *,
        filename: str,
        media_type: str,
        raw: bytes,
    ):
        return self.registry.derive_text(
            filename=filename,
            declared_media_type=media_type,
            storage_media_type=media_type,
            raw=raw,
        )

    def test_utf8_text_markdown_yaml_and_source_code_are_supported(
        self,
    ) -> None:
        cases = (
            ("plain.txt", "text/plain", b"line 1\r\nline 2\r\n", "line 1\nline 2\n"),
            ("doc.md", "text/markdown", b"# Title\r\n", "# Title\n"),
            ("data.yaml", "application/yaml", b"key: value\r\n", "key: value\n"),
            ("main.py", "text/x-python", b"print('ok')\r\n", "print('ok')\n"),
        )

        for filename, media_type, raw, expected in cases:
            with self.subTest(filename=filename):
                derived = self.derive(
                    filename=filename,
                    media_type=media_type,
                    raw=raw,
                )
                self.assertEqual(
                    derived.derived_text,
                    expected,
                )
                self.assertEqual(
                    derived.metadata["locator_count"],
                    1,
                )

    def test_json_is_validated_and_hashed_deterministically(
        self,
    ) -> None:
        raw = b"{\"b\":2,\r\n\"a\":1}\r\n"
        derived = self.derive(
            filename="data.json",
            media_type="application/json",
            raw=raw,
        )

        self.assertEqual(
            derived.derived_text,
            "{\"b\":2,\n\"a\":1}\n",
        )
        self.assertEqual(
            hashlib.sha256(
                derived.derived_bytes
            ).hexdigest(),
            hashlib.sha256(
                b"{\"b\":2,\n\"a\":1}\n"
            ).hexdigest(),
        )

        with self.assertRaises(
            InvalidDocumentError
        ):
            self.derive(
                filename="broken.json",
                media_type="application/json",
                raw=b"{",
            )

    def test_html_is_sanitized_without_active_content(
        self,
    ) -> None:
        derived = self.derive(
            filename="page.html",
            media_type="text/html",
            raw=(
                b"<html><body><h1>Title</h1>"
                b"<script>alert(1)</script>"
                b"<p onclick='evil()'>Visible</p>"
                b"<style>body{display:none}</style>"
                b"</body></html>"
            ),
        )

        self.assertEqual(
            derived.derived_text,
            "Title\nVisible",
        )
        self.assertEqual(
            derived.adapter_name,
            "html_sanitizer",
        )

    def test_docx_paragraph_and_table_order_is_preserved(
        self,
    ) -> None:
        derived = self.derive(
            filename="doc.docx",
            media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            raw=make_docx(
                ("paragraph", "Alpha"),
                ("table", "A\tB"),
                ("paragraph", "Omega"),
            ),
        )

        self.assertEqual(
            derived.derived_text,
            "Alpha\n\nA\tB\n\nOmega",
        )
        self.assertEqual(
            derived.metadata["locator_count"],
            3,
        )

    def test_pdf_page_extraction_and_no_text_rejection(
        self,
    ) -> None:
        derived = self.derive(
            filename="scan.pdf",
            media_type="application/pdf",
            raw=make_pdf("Page One", "Page Two"),
        )

        self.assertEqual(
            derived.derived_text,
            "Page One\n\nPage Two",
        )
        self.assertEqual(
            derived.metadata["page_count"],
            2,
        )

        with self.assertRaises(
            NoExtractableTextError
        ):
            self.derive(
                filename="blank.pdf",
                media_type="application/pdf",
                raw=make_pdf("", ""),
            )

    def test_pdf_dependency_unavailable_is_deterministic(
        self,
    ) -> None:
        with patch.dict(
            sys.modules,
            {"pypdf": None},
        ):
            with self.assertRaises(
                DependencyUnavailableError
            ):
                self.derive(
                    filename="scan.pdf",
                    media_type="application/pdf",
                    raw=make_pdf("Page One"),
                )


if __name__ == "__main__":
    unittest.main()
