from datetime import UTC, datetime


def markdown_to_plain_text(markdown: str) -> str:
    lines = []
    for line in markdown.split("\n"):
        stripped = line.lstrip("#").strip() if line.startswith("#") else line
        lines.append(stripped.replace("`", ""))
    return "\n".join(lines)


def render_markdown_pdf(markdown: str) -> bytes:
    # Imported locally, matching document_adapters.py's pypdf import -
    # fpdf2 pulls in `cryptography` transitively (for optional PDF
    # encryption) purely as an import-time side effect, unrelated to
    # anything this function actually uses. A module-level import here
    # would force every caller of this module to pay for that import even
    # when never rendering a PDF.
    try:
        from fpdf import FPDF
        from fpdf.enums import XPos, YPos
    except ModuleNotFoundError:
        return _render_minimal_pdf(markdown)

    # fpdf2's core fonts (Helvetica/Times/Courier) only cover
    # Latin-1/WinAnsi - no Unicode TTF is bundled with this app, so source
    # text containing characters outside that range is replaced rather
    # than crashing rendering. This keeps PDF generation dependency-free
    # (no external font asset to ship).
    pdf = FPDF(format="A4")
    # fpdf2 stamps /CreationDate from the wall clock by default, so
    # re-rendering byte-identical markdown at a different moment would
    # produce different PDF bytes - and, for exports.py's callers, a
    # different sha256/bundle_fingerprint even though nothing about the
    # underlying content changed. A fixed sentinel date keeps rendering
    # fully deterministic.
    pdf.set_creation_date(datetime(1980, 1, 1, tzinfo=UTC))
    pdf.set_auto_page_break(auto=True, margin=15)
    pdf.add_page()

    # multi_cell's cursor does not return to the left margin by default
    # (new_x defaults to XPos.RIGHT) - without pinning it back to
    # LMARGIN/NEXT after every call, the cursor drifts toward the page's
    # right edge on each successive line until there is no width left to
    # render even a single character.
    for line in markdown.split("\n"):
        safe_line = (
            line.replace("`", "")
            .replace("→", "->")
            .encode("latin-1", errors="replace")
            .decode("latin-1")
        )
        if safe_line.startswith("### "):
            pdf.set_font("helvetica", "B", 12)
            pdf.multi_cell(0, 7, safe_line[4:], new_x=XPos.LMARGIN, new_y=YPos.NEXT)
        elif safe_line.startswith("## "):
            pdf.set_font("helvetica", "B", 14)
            pdf.multi_cell(0, 8, safe_line[3:], new_x=XPos.LMARGIN, new_y=YPos.NEXT)
        elif safe_line.startswith("# "):
            pdf.set_font("helvetica", "B", 18)
            pdf.multi_cell(0, 10, safe_line[2:], new_x=XPos.LMARGIN, new_y=YPos.NEXT)
        elif safe_line.strip() == "":
            pdf.set_font("helvetica", "", 10)
            pdf.ln(4)
        else:
            pdf.set_font("helvetica", "", 10)
            pdf.multi_cell(0, 6, safe_line, new_x=XPos.LMARGIN, new_y=YPos.NEXT)

    return bytes(pdf.output())


def _render_minimal_pdf(markdown: str) -> bytes:
    plain = markdown_to_plain_text(markdown)
    lines = [
        _pdf_escape(line[:100])
        for line in plain.splitlines()[:90]
    ]
    stream_lines = [
        "BT",
        "/F1 10 Tf",
        "50 800 Td",
        "14 TL",
    ]
    for index, line in enumerate(lines):
        if index > 0:
            stream_lines.append("T*")
        stream_lines.append(f"({line}) Tj")
    stream_lines.append("ET")
    stream = "\n".join(stream_lines).encode("latin-1")
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        (
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
            b"/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>"
        ),
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        (
            f"<< /Length {len(stream)} >>\nstream\n".encode("ascii")
            + stream
            + b"\nendstream"
        ),
    ]
    output = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for number, obj in enumerate(objects, start=1):
        offsets.append(len(output))
        output.extend(f"{number} 0 obj\n".encode("ascii"))
        output.extend(obj)
        output.extend(b"\nendobj\n")
    xref_offset = len(output)
    output.extend(f"xref\n0 {len(objects) + 1}\n".encode("ascii"))
    output.extend(b"0000000000 65535 f \n")
    for offset in offsets[1:]:
        output.extend(f"{offset:010d} 00000 n \n".encode("ascii"))
    output.extend(
        (
            "trailer\n"
            f"<< /Size {len(objects) + 1} /Root 1 0 R >>\n"
            "startxref\n"
            f"{xref_offset}\n"
            "%%EOF\n"
        ).encode("ascii")
    )
    return bytes(output)


def _pdf_escape(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .encode("latin-1", errors="replace")
        .decode("latin-1")
    )
