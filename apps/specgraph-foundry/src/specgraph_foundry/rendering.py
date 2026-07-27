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
    from fpdf import FPDF
    from fpdf.enums import XPos, YPos

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
