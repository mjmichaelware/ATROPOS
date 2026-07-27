import hashlib
import json
import tempfile
import unittest
import uuid
from io import BytesIO
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import unquote, urlsplit
from zipfile import ZIP_DEFLATED, ZipFile

from specgraph_foundry.database import Database
from specgraph_foundry.http_api.auth import (
    AuthenticationError,
)
from specgraph_foundry.http_api.gateway import (
    AuthenticatedApi,
    new_request,
)
from specgraph_foundry.http_api.models import (
    Principal,
)
from specgraph_foundry.http_api.source_uploads import (
    SourceUploadService,
    SourceUploadSettings,
)
from specgraph_foundry.http_api.storage import (
    SupabaseStorageClient,
)


def make_docx(
    paragraphs: list[str],
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
        body = "".join(
            "<w:p><w:r><w:t>"
            + paragraph
            + "</w:t></w:r></w:p>"
            for paragraph in paragraphs
        )
        archive.writestr(
            "word/document.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>"""
            + body
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


class FakeAuthenticator:
    def __init__(
        self,
        principal: Principal,
    ) -> None:
        self.principal = principal

    def authenticate(
        self,
        authorization: str | None,
    ) -> Principal:
        if authorization != "Bearer valid":
            raise AuthenticationError(
                "valid bearer token required"
            )
        return self.principal


class FakeResponse:
    def __init__(
        self,
        payload: bytes,
        *,
        url: str,
        headers: dict[str, str] | None = None,
    ) -> None:
        self.payload = payload
        self.url = url
        self.headers = headers or {}

    def __enter__(self):
        return self

    def __exit__(
        self,
        exception_type,
        exception,
        traceback,
    ) -> bool:
        return False

    def read(
        self,
        size: int = -1,
    ) -> bytes:
        if size < 0:
            return self.payload
        return self.payload[:size]

    def geturl(self) -> str:
        return self.url


class FakeStorageTransport:
    def __init__(
        self,
        origin: str,
    ) -> None:
        self.origin = origin.rstrip("/")
        self.objects: dict[
            tuple[str, str],
            tuple[bytes, str]
        ] = {}

    def store_object(
        self,
        bucket: str,
        object_path: str,
        data: bytes,
        media_type: str,
    ) -> None:
        self.objects[(bucket, object_path)] = (
            data,
            media_type,
        )

    def __call__(
        self,
        request,
        timeout,
    ):
        del timeout
        path = urlsplit(
            request.full_url
        ).path

        if path.startswith(
            "/storage/v1/object/upload/sign/"
        ):
            suffix = path.removeprefix(
                "/storage/v1/object/upload/sign/"
            )
            bucket, _, object_path = suffix.partition(
                "/"
            )
            payload = json.dumps(
                {
                    "url": (
                        f"{self.origin}/storage/v1/object/upload/"
                        f"{bucket}/{object_path}?token=signed"
                    )
                }
            ).encode("utf-8")
            return FakeResponse(
                payload,
                url=request.full_url,
            )

        if path.startswith(
            "/storage/v1/object/"
        ):
            suffix = path.removeprefix(
                "/storage/v1/object/"
            )
            bucket, _, object_path = suffix.partition(
                "/"
            )
            key = (
                unquote(bucket),
                unquote(object_path),
            )
            if key not in self.objects:
                raise HTTPError(
                    request.full_url,
                    404,
                    "missing",
                    {},
                    None,
                )
            payload, media_type = self.objects[key]
            return FakeResponse(
                payload,
                url=request.full_url,
                headers={"content-type": media_type},
            )

        raise AssertionError(path)


class DerivationProvenanceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.database = Database(
            Path(self.temp.name)
            / "derivation.sqlite3"
        )
        self.database.initialize()
        self.principal = Principal(
            user_id=str(uuid.uuid4()),
            email="owner@example.com",
        )
        self.transport = FakeStorageTransport(
            "https://example.supabase.co"
        )
        self.source_uploads = SourceUploadService(
            self.database,
            SupabaseStorageClient(
                "https://example.supabase.co",
                "anon-key",
                timeout_seconds=5,
                opener=self.transport,
            ),
            SourceUploadSettings(
                bucket="source-documents",
                upload_url_ttl_seconds=900,
                max_source_bytes=100_000,
            ),
        )
        self.application = AuthenticatedApi(
            self.database,
            FakeAuthenticator(
                self.principal
            ),
            source_uploads=self.source_uploads,
            enforce_mutation_guards=True,
        )
        created = self.request(
            "POST",
            "/v1/projects",
            {
                "slug": "derivation-project",
                "name": "Derivation Project",
            },
        )
        self.project_id = str(created.body["id"])

    def tearDown(self) -> None:
        self.temp.cleanup()

    def request(
        self,
        method: str,
        path: str,
        payload: dict[str, object] | None = None,
        *,
        idempotency_key: str | None = None,
        principal: Principal | None = None,
    ):
        headers = {
            "Authorization": "Bearer valid"
        }
        if idempotency_key is not None:
            headers["Idempotency-Key"] = idempotency_key
        application = self.application
        if principal is not None:
            application = AuthenticatedApi(
                self.database,
                FakeAuthenticator(principal),
                source_uploads=self.source_uploads,
                enforce_mutation_guards=True,
            )
        return application.dispatch(
            new_request(
                method,
                path,
                headers,
                payload or {},
            )
        )

    def create_upload(
        self,
        filename: str,
        media_type: str,
        raw: bytes,
        *,
        key: str,
    ):
        digest = hashlib.sha256(raw).hexdigest()
        created = self.request(
            "POST",
            f"/v1/projects/{self.project_id}/source-uploads",
            {
                "filename": filename,
                "media_type": media_type,
                "byte_size": len(raw),
                "sha256": digest,
            },
            idempotency_key=key,
        )
        self.transport.store_object(
            "source-documents",
            str(created.body["object_path"]),
            raw,
            media_type,
        )
        return created

    def test_finalized_derivation_and_provenance_distinguish_raw_and_derived_authority(
        self,
    ) -> None:
        raw = make_docx(
            [
                "Alpha",
                "Beta",
                "Gamma",
                "Delta",
                "Epsilon",
                "Zeta",
            ]
        )
        upload = self.create_upload(
            "report.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            raw,
            key="docx-upload-key-0001",
        )

        finalized = self.request(
            "POST",
            f"/v1/source-uploads/{upload.body['id']}/finalize",
            {},
            idempotency_key="docx-finalize-key-0001",
        )
        replay = self.request(
            "POST",
            f"/v1/source-uploads/{upload.body['id']}/finalize",
            {},
            idempotency_key="docx-finalize-key-0001",
        )
        provenance = self.request(
            "GET",
            f"/v1/documents/{finalized.body['document_id']}/provenance",
        )

        derived_text = "Alpha\n\nBeta\n\nGamma\n\nDelta\n\nEpsilon\n\nZeta"
        derived_sha256 = hashlib.sha256(
            derived_text.encode("utf-8")
        ).hexdigest()

        self.assertEqual(finalized.status, 201)
        self.assertEqual(
            replay.headers["idempotency-replayed"],
            "true",
        )
        self.assertEqual(
            finalized.body["raw_authority"]["sha256"],
            hashlib.sha256(raw).hexdigest(),
        )
        self.assertEqual(
            finalized.body["derivation"]["derived_sha256"],
            derived_sha256,
        )
        self.assertNotEqual(
            finalized.body["raw_authority"]["sha256"],
            finalized.body["derivation"]["derived_sha256"],
        )
        self.assertEqual(
            provenance.body["provenance"]["raw_authority"]["sha256"],
            hashlib.sha256(raw).hexdigest(),
        )
        self.assertEqual(
            provenance.body["provenance"]["derivation"]["derived_sha256"],
            derived_sha256,
        )
        self.assertEqual(
            provenance.body["provenance"]["derivation"]["locators_count"],
            6,
        )
        self.assertTrue(
            provenance.body["provenance"]["derivation"]["locators_has_more"]
        )
        self.assertEqual(
            len(provenance.body["provenance"]["derivation"]["locators_preview"]),
            5,
        )
        self.assertEqual(
            provenance.body["provenance"]["byte_end"],
            len(derived_text.encode("utf-8")),
        )

        with self.database.connect() as connection:
            derivation_count = connection.execute(
                """
                SELECT COUNT(*) AS value
                FROM document_derivations
                """
            ).fetchone()["value"]
            document_count = connection.execute(
                """
                SELECT COUNT(*) AS value
                FROM source_documents
                """
            ).fetchone()["value"]

        self.assertEqual(int(derivation_count), 1)
        self.assertEqual(int(document_count), 1)

    def test_failed_adapter_does_not_create_document_or_derivation(
        self,
    ) -> None:
        raw = make_pdf("", "")
        upload = self.create_upload(
            "blank.pdf",
            "application/pdf",
            raw,
            key="pdf-upload-key-0001",
        )

        finalized = self.request(
            "POST",
            f"/v1/source-uploads/{upload.body['id']}/finalize",
            {},
            idempotency_key="pdf-finalize-key-0001",
        )

        self.assertEqual(finalized.status, 415)
        self.assertEqual(
            finalized.body["error"]["code"],
            "NO_EXTRACTABLE_TEXT",
        )

        with self.database.connect() as connection:
            derivation_count = connection.execute(
                """
                SELECT COUNT(*) AS value
                FROM document_derivations
                """
            ).fetchone()["value"]
            document_count = connection.execute(
                """
                SELECT COUNT(*) AS value
                FROM source_documents
                """
            ).fetchone()["value"]

        self.assertEqual(int(derivation_count), 0)
        self.assertEqual(int(document_count), 0)

    def test_outsider_access_remains_non_enumerating(
        self,
    ) -> None:
        raw = make_docx(["Owner only"])
        upload = self.create_upload(
            "owner.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            raw,
            key="docx-upload-key-0002",
        )
        finalized = self.request(
            "POST",
            f"/v1/source-uploads/{upload.body['id']}/finalize",
            {},
            idempotency_key="docx-finalize-key-0002",
        )

        outsider = Principal(
            user_id=str(uuid.uuid4()),
            email="other@example.com",
        )

        hidden_upload = self.request(
            "GET",
            f"/v1/source-uploads/{upload.body['id']}",
            principal=outsider,
        )
        hidden_doc = self.request(
            "GET",
            f"/v1/documents/{finalized.body['document_id']}/provenance",
            principal=outsider,
        )

        self.assertEqual(hidden_upload.status, 404)
        self.assertEqual(hidden_doc.status, 404)


if __name__ == "__main__":
    unittest.main()
