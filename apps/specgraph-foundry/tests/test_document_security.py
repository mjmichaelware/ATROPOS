import unittest
from io import BytesIO
from zipfile import ZIP_DEFLATED, ZipInfo, ZipFile

from specgraph_foundry.http_api.document_adapters import (
    DocumentAdapterRegistry,
)
from specgraph_foundry.http_api.document_security import (
    DocumentEncryptedError,
    DocumentLimitExceededError,
    DocumentSecurityLimits,
    InvalidDocumentError,
)


def make_docx_archive(
    files: dict[str, bytes],
) -> bytes:
    buffer = BytesIO()
    with ZipFile(
        buffer,
        "w",
        ZIP_DEFLATED,
    ) as archive:
        for name, payload in files.items():
            archive.writestr(name, payload)
    return buffer.getvalue()


def make_minimal_docx(
    *,
    body: str = "<w:p><w:r><w:t>Hello</w:t></w:r></w:p>",
    rels: bytes | None = None,
    extras: dict[str, bytes] | None = None,
) -> bytes:
    files = {
        "[Content_Types].xml": b"""<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""",
        "_rels/.rels": rels
        or b"""<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""",
        "word/document.xml": (
            """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>"""
            + body
            + """</w:body>
</w:document>"""
        ).encode("utf-8"),
    }

    if extras:
        files.update(extras)

    return make_docx_archive(files)


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
    content_ids = [next_object + i for i in range(len(page_texts))]
    font_id = next_object + len(page_texts)

    for index in range(len(page_texts)):
        objects.append(
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] "
            + b"/Contents "
            + f"{content_ids[index]} 0 R".encode()
            + b" /Resources << /Font << /F1 "
            + f"{font_id} 0 R".encode()
            + b" >> >> >>"
        )

    for text in page_texts:
        stream = (
            "BT\n/F1 24 Tf\n72 72 Td\n("
            + text
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


class DocumentSecurityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.registry = DocumentAdapterRegistry(
            DocumentSecurityLimits(
                max_original_bytes=200_000,
                max_extracted_text_bytes=1024,
                max_pdf_pages=2,
                max_docx_archive_members=4,
                max_docx_expanded_bytes=2048,
                max_archive_compression_ratio=4,
                max_json_nesting=4,
                max_html_tokens=20,
                max_html_nesting=8,
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

    def test_mime_spoofing_extension_mismatch_and_invalid_utf8_are_rejected(
        self,
    ) -> None:
        with self.assertRaises(
            InvalidDocumentError
        ):
            self.derive(
                filename="report.pdf",
                media_type="text/plain",
                raw=b"plain text",
            )

        with self.assertRaises(
            InvalidDocumentError
        ):
            self.derive(
                filename="broken.txt",
                media_type="text/plain",
                raw=b"\xff\xfe",
            )

    def test_json_html_and_text_limits_are_enforced(
        self,
    ) -> None:
        with self.assertRaises(
            DocumentLimitExceededError
        ):
            self.derive(
                filename="deep.json",
                media_type="application/json",
                raw=b'{"a":{"b":{"c":{"d":{"e":1}}}}}',
            )

        with self.assertRaises(
            DocumentLimitExceededError
        ):
            self.derive(
                filename="page.html",
                media_type="text/html",
                raw=b"<div>" * 10 + b"x" + b"</div>" * 10,
            )

        with self.assertRaises(
            InvalidDocumentError
        ):
            self.derive(
                filename="nul.txt",
                media_type="text/plain",
                raw=b"a\x00b",
            )

    def test_docx_traversal_external_relationships_and_macros_are_rejected(
        self,
    ) -> None:
        with self.assertRaises(
            InvalidDocumentError
        ):
            self.derive(
                filename="attack.docx",
                media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                raw=make_docx_archive(
                    {
                        "[Content_Types].xml": b"x",
                        "_rels/.rels": b"x",
                        "../word/document.xml": b"x",
                    }
                ),
            )

        with self.assertRaises(
            InvalidDocumentError
        ):
            self.derive(
                filename="external.docx",
                media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                raw=make_minimal_docx(
                    rels=b"""<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="https://evil.example/pixel.png" TargetMode="External"/>
</Relationships>"""
                ),
            )

        with self.assertRaises(
            InvalidDocumentError
        ):
            self.derive(
                filename="macro.docx",
                media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                raw=make_minimal_docx(
                    extras={"word/vbaProject.bin": b"macro"}
                ),
            )

    def test_docx_member_count_and_compression_limits_are_enforced(
        self,
    ) -> None:
        with self.assertRaises(
            DocumentLimitExceededError
        ):
            self.derive(
                filename="many.docx",
                media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                raw=make_minimal_docx(
                    extras={
                        "word/a.xml": b"a",
                        "word/b.xml": b"b",
                    }
                ),
            )

        large = BytesIO()
        with ZipFile(
            large,
            "w",
            ZIP_DEFLATED,
        ) as archive:
            archive.writestr("[Content_Types].xml", b"x")
            archive.writestr("_rels/.rels", b"x")
            payload = b"A" * 4096
            info = ZipInfo("word/document.xml")
            archive.writestr(info, payload)

        with self.assertRaises(
            DocumentLimitExceededError
        ):
            self.derive(
                filename="ratio.docx",
                media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                raw=large.getvalue(),
            )

    def test_encrypted_and_oversized_pdf_are_rejected(
        self,
    ) -> None:
        from pypdf import PdfReader, PdfWriter

        writer = PdfWriter()
        writer.append(BytesIO(make_pdf("Secret")))
        writer.encrypt("locked")
        encrypted = BytesIO()
        writer.write(encrypted)

        with self.assertRaises(
            DocumentEncryptedError
        ):
            self.derive(
                filename="secret.pdf",
                media_type="application/pdf",
                raw=encrypted.getvalue(),
            )

        with self.assertRaises(
            DocumentLimitExceededError
        ):
            self.derive(
                filename="many-pages.pdf",
                media_type="application/pdf",
                raw=make_pdf("A", "B", "C"),
            )


if __name__ == "__main__":
    unittest.main()
