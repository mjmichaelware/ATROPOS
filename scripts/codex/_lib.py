from __future__ import annotations

import dataclasses
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Iterator
from xml.etree import ElementTree as ET

REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = REPO_ROOT / ".atropos/source-authority/original"
MANIFEST_PATH = REPO_ROOT / ".atropos/source-authority/SOURCE_MANIFEST.tsv"
CONTEXT_CACHE_ROOT = REPO_ROOT / ".atropos/context-cache"
GATE_CACHE_ROOT = REPO_ROOT / ".atropos/gate-cache"
HANDOFF_ROOT = REPO_ROOT / ".atropos/handoffs"

SCHEMA_VERSION = 1
TOOL_VERSION = "1"

SECRET_NAME_RE = re.compile(r"(SECRET|TOKEN|PASSWORD|PASS|PRIVATE|KEY|BEARER|COOKIE|SESSION|AUTH)", re.I)
HEADING_MARKDOWN_RE = re.compile(r"^\s{0,3}(#{1,6})\s+(.*\S)\s*$")
HEADING_PHASE_RE = re.compile(r"^\s*(Phase\s+\d+[:.]?.*)$")
HEADING_NUMBERED_RE = re.compile(r"^\s*(\d+(?:\.\d+)*)(?:[.)])?\s+(.*\S)\s*$")
HEADING_SECTION_RE = re.compile(r"^\s*(Section\s+\d+[:.]?.*)$", re.I)
HEADING_CAPS_RE = re.compile(r"^[A-Z0-9][A-Z0-9 .,&/()\-–—'\":]+$")
STYLE_HEADING_RE = re.compile(r"^Heading(?:\s*|\D*)(\d+)$", re.I)
STYLE_TITLE_RE = re.compile(r"^(Title|Subtitle)$", re.I)


@dataclass(frozen=True)
class ManifestEntry:
    sha256: str
    size_bytes: int
    original_path: Path
    download_path: str
    source_id: str
    original_filename: str
    title: str
    family_key: str
    version_key: str
    extension: str


@dataclass(frozen=True)
class LineRecord:
    line_no: int
    text: str
    page_no: int | None = None
    page_line_no: int | None = None
    paragraph_no: int | None = None
    style_name: str | None = None
    heading_level_hint: int = 0


@dataclass(frozen=True)
class SectionRecord:
    section_id: str
    heading: str | None
    heading_path: list[str]
    heading_level: int
    start_line: int
    end_line: int
    start_page: int | None
    end_page: int | None
    start_paragraph: int | None
    end_paragraph: int | None
    normalized_text: str
    normalized_sha256: str
    token_estimate: int


@dataclass(frozen=True)
class ExtractionRecord:
    source_id: str
    original_filename: str
    sha256: str
    size_bytes: int
    kind: str
    text: str
    lines: list[LineRecord]
    page_count: int | None = None
    paragraph_count: int | None = None
    style_map: dict[str, str] | None = None


def repo_root() -> Path:
    return REPO_ROOT


def ensure_dir(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    return path


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_text(text: str) -> str:
    return sha256_bytes(text.encode("utf-8"))


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def canonical_json(data: Any) -> str:
    return json.dumps(data, sort_keys=True, ensure_ascii=False, separators=(",", ":"))


def fingerprint(data: Any) -> str:
    return sha256_text(canonical_json(data))


def atomic_write_bytes(path: Path, data: bytes) -> None:
    ensure_dir(path.parent)
    tmp = path.with_name(f".{path.name}.tmp-{os.getpid()}-{secrets_token(8)}")
    with tmp.open("wb") as fh:
        fh.write(data)
        fh.flush()
        os.fsync(fh.fileno())
    os.replace(tmp, path)


def atomic_write_text(path: Path, text: str) -> None:
    atomic_write_bytes(path, text.encode("utf-8"))


def atomic_write_json(path: Path, data: Any) -> None:
    atomic_write_text(path, json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n")


def secrets_token(length: int = 12) -> str:
    return hashlib.sha256(f"{os.getpid()}:{os.urandom(16).hex()}".encode("utf-8")).hexdigest()[:length]


def normalize_text(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n").lstrip("\ufeff")


def decode_text(data: bytes) -> str:
    for encoding in ("utf-8-sig", "utf-8"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    return data.decode("latin-1")


def load_manifest(path: Path = MANIFEST_PATH) -> list[ManifestEntry]:
    entries: list[ManifestEntry] = []
    for raw_line in path.read_text("utf-8").splitlines():
        if not raw_line.strip():
            continue
        parts = raw_line.split("\t")
        if len(parts) < 4:
            continue
        if parts[0].lower() == "sha256" or parts[1].lower() == "bytes":
            continue
        sha, size, source_path, download_path = parts[:4]
        original_path = Path(source_path)
        filename = original_path.name
        source_id, title = split_source_name(filename)
        family_key, version_key = derive_family_and_version(title)
        entries.append(
            ManifestEntry(
                sha256=sha.strip(),
                size_bytes=int(size),
                original_path=original_path,
                download_path=download_path,
                source_id=source_id,
                original_filename=filename,
                title=title,
                family_key=family_key,
                version_key=version_key,
                extension=original_path.suffix.lower(),
            )
        )
    return entries


def split_source_name(filename: str) -> tuple[str, str]:
    if "__" in filename:
        source_id, title = filename.split("__", 1)
        return source_id, title
    stem = Path(filename).stem
    return stem[:16], stem


def derive_family_and_version(title: str) -> tuple[str, str]:
    stem = title
    version_bits: list[str] = []

    m = re.search(r"\bv(\d+(?:\.\d+)*)\b", stem, re.I)
    if m:
        version_bits.append(f"v{m.group(1)}")

    for prefix in ("part", "pass", "phase", "section"):
        m = re.search(rf"\b{prefix}[\s._-]*(\d+)\b", stem, re.I)
        if m:
            version_bits.append(f"{prefix}{int(m.group(1)):03d}")

    m = re.search(r"\b(\d{4}-\d{2}-\d{2})\b", stem)
    if m:
        version_bits.append(m.group(1))

    family = stem
    family = re.sub(r"\bv(\d+(?:\.\d+)*)\b", "", family, flags=re.I)
    family = re.sub(r"\b(part|pass|phase|section)[\s._-]*\d+\b", "", family, flags=re.I)
    family = re.sub(r"\b\d{4}-\d{2}-\d{2}\b", "", family)
    family = re.sub(r"[._]+", " ", family)
    family = re.sub(r"\s+", " ", family).strip(" -_")
    if not family:
        family = stem
    version = " ".join(version_bits).strip() or "unversioned"
    return family, version


def verify_manifest(entries: list[ManifestEntry]) -> list[str]:
    problems: list[str] = []
    for entry in entries:
        if not entry.original_path.exists():
            problems.append(f"missing: {entry.original_path}")
            continue
        size = entry.original_path.stat().st_size
        if size != entry.size_bytes:
            problems.append(f"size mismatch: {entry.original_path} manifest={entry.size_bytes} actual={size}")
        digest = sha256_file(entry.original_path)
        if digest != entry.sha256:
            problems.append(f"sha mismatch: {entry.original_path} manifest={entry.sha256} actual={digest}")
    return problems


def detect_kind(path: Path, raw: bytes) -> str:
    ext = path.suffix.lower()
    if raw.startswith(b"%PDF-") or ext == ".pdf":
        return "pdf"
    if ext == ".docx" or (zipfile.is_zipfile(path) and _zip_has_docx_entries(path)):
        return "docx"
    if ext == ".md":
        return "markdown"
    if ext == ".tsv":
        return "tabular"
    if ext == ".txt":
        return "text"
    return "text" if _looks_like_text(raw) else "binary"


def _looks_like_text(raw: bytes) -> bool:
    if b"\x00" in raw:
        return False
    sample = raw[:4096]
    if not sample:
        return True
    try:
        sample.decode("utf-8")
        return True
    except UnicodeDecodeError:
        return False


def _zip_has_docx_entries(path: Path) -> bool:
    with zipfile.ZipFile(path) as zf:
        names = set(zf.namelist())
    return "[Content_Types].xml" in names and "word/document.xml" in names


def extract_source(entry: ManifestEntry) -> ExtractionRecord:
    raw = entry.original_path.read_bytes()
    kind = detect_kind(entry.original_path, raw)
    if kind == "binary":
        raise ValueError(f"unsupported binary source: {entry.original_path}")

    if kind == "pdf":
        text = extract_pdf_text(entry.original_path)
        lines = extract_pdf_lines(text)
        page_count = text.count("\f") + (1 if text else 0)
        return ExtractionRecord(
            source_id=entry.source_id,
            original_filename=entry.original_filename,
            sha256=entry.sha256,
            size_bytes=entry.size_bytes,
            kind=kind,
            text=normalize_text(text),
            lines=lines,
            page_count=page_count,
            paragraph_count=None,
        )

    if kind == "docx":
        text, lines, style_map = extract_docx_text(entry.original_path)
        paragraph_count = sum(1 for line in lines if line.paragraph_no is not None)
        return ExtractionRecord(
            source_id=entry.source_id,
            original_filename=entry.original_filename,
            sha256=entry.sha256,
            size_bytes=entry.size_bytes,
            kind=kind,
            text=normalize_text(text),
            lines=lines,
            page_count=None,
            paragraph_count=paragraph_count,
            style_map=style_map,
        )

    text = decode_text(raw)
    text = normalize_text(text)
    lines = extract_plain_lines(text, kind=kind)
    paragraph_count = max((line.paragraph_no or 0) for line in lines) if lines else 0
    return ExtractionRecord(
        source_id=entry.source_id,
        original_filename=entry.original_filename,
        sha256=entry.sha256,
        size_bytes=entry.size_bytes,
        kind=kind,
        text=text,
        lines=lines,
        page_count=None,
        paragraph_count=paragraph_count,
    )


def extract_pdf_text(path: Path) -> str:
    cmd = ["pdftotext", "-layout", "-enc", "UTF-8", str(path), "-"]
    return subprocess.check_output(cmd, text=True, stderr=subprocess.DEVNULL)


def extract_pdf_lines(text: str) -> list[LineRecord]:
    normalized = normalize_text(text)
    lines: list[LineRecord] = []
    page_no = 1
    page_line_no = 0
    paragraph_no = 0
    in_paragraph = False
    global_line_no = 0
    for page_text in normalized.split("\f"):
        page_line_no = 0
        for raw_line in page_text.split("\n"):
            global_line_no += 1
            page_line_no += 1
            stripped = raw_line.strip()
            if stripped:
                if not in_paragraph:
                    paragraph_no += 1
                    in_paragraph = True
                paragraph = paragraph_no
            else:
                in_paragraph = False
                paragraph = None
            lines.append(
                LineRecord(
                    line_no=global_line_no,
                    page_no=page_no,
                    page_line_no=page_line_no,
                    paragraph_no=paragraph,
                    text=raw_line,
                )
            )
        page_no += 1
    return lines


def extract_docx_text(path: Path) -> tuple[str, list[LineRecord], dict[str, str]]:
    with zipfile.ZipFile(path) as zf:
        document_xml = zf.read("word/document.xml")
        styles_xml = zf.read("word/styles.xml") if "word/styles.xml" in zf.namelist() else None

    style_map = parse_docx_styles(styles_xml) if styles_xml else {}
    ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
    root = ET.fromstring(document_xml)
    lines: list[LineRecord] = []
    paras: list[str] = []
    for idx, para in enumerate(root.findall(".//w:p", ns), 1):
        text = "".join(node.text or "" for node in para.findall(".//w:t", ns))
        ppr = para.find("w:pPr", ns)
        style_name = None
        heading_level = 0
        if ppr is not None:
            style = ppr.find("w:pStyle", ns)
            if style is not None:
                style_id = style.attrib.get(f"{{{ns['w']}}}val", "")
                style_name = style_map.get(style_id, style_id or None)
                heading_level = docx_heading_level(style_name or style_id or "")
            outline = ppr.find("w:outlineLvl", ns)
            if outline is not None:
                try:
                    heading_level = max(heading_level, int(outline.attrib.get(f"{{{ns['w']}}}val", "0")) + 1)
                except ValueError:
                    pass
        paras.append(text)
        lines.append(
            LineRecord(
                line_no=idx,
                text=text,
                paragraph_no=idx,
                style_name=style_name,
                heading_level_hint=heading_level,
            )
        )
    return normalize_text("\n".join(paras)), lines, style_map


def parse_docx_styles(styles_xml: bytes) -> dict[str, str]:
    ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
    root = ET.fromstring(styles_xml)
    style_map: dict[str, str] = {}
    for style in root.findall(".//w:style", ns):
        style_id = style.attrib.get(f"{{{ns['w']}}}styleId")
        if not style_id:
            continue
        name_el = style.find("w:name", ns)
        if name_el is not None:
            style_name = name_el.attrib.get(f"{{{ns['w']}}}val", style_id)
        else:
            style_name = style_id
        style_map[style_id] = style_name
    return style_map


def docx_heading_level(style_name: str) -> int:
    if not style_name:
        return 0
    m = STYLE_HEADING_RE.match(style_name)
    if m:
        return int(m.group(1))
    if STYLE_TITLE_RE.match(style_name):
        return 1
    return 0


def extract_plain_lines(text: str, kind: str = "text") -> list[LineRecord]:
    lines: list[LineRecord] = []
    paragraph_no = 0
    in_paragraph = False
    for idx, raw_line in enumerate(text.split("\n"), 1):
        stripped = raw_line.strip()
        if stripped:
            if not in_paragraph:
                paragraph_no += 1
                in_paragraph = True
            paragraph = paragraph_no
        else:
            in_paragraph = False
            paragraph = None
        lines.append(
            LineRecord(
                line_no=idx,
                page_no=1 if kind != "pdf" else None,
                page_line_no=idx,
                paragraph_no=paragraph,
                text=raw_line,
            )
        )
    return lines


def is_heading_line(line: LineRecord) -> bool:
    text = line.text.strip()
    if not text:
        return False
    if line.heading_level_hint > 0:
        return True
    if HEADING_MARKDOWN_RE.match(text):
        return True
    if HEADING_PHASE_RE.match(text):
        return True
    if HEADING_SECTION_RE.match(text):
        return True
    if HEADING_NUMBERED_RE.match(text):
        return True
    if len(text) <= 100 and HEADING_CAPS_RE.match(text) and any(ch.isalpha() for ch in text):
        return True
    return False


def heading_level(line: LineRecord) -> int:
    if line.heading_level_hint > 0:
        return line.heading_level_hint
    text = line.text.strip()
    if HEADING_MARKDOWN_RE.match(text):
        return len(HEADING_MARKDOWN_RE.match(text).group(1))
    if HEADING_PHASE_RE.match(text):
        return 1
    if HEADING_SECTION_RE.match(text):
        return 2
    m = HEADING_NUMBERED_RE.match(text)
    if m:
        return len(m.group(1).split("."))
    if len(text) <= 100 and HEADING_CAPS_RE.match(text) and any(ch.isalpha() for ch in text):
        return 2
    return 0


def clean_heading(text: str) -> str:
    stripped = text.strip()
    m = HEADING_MARKDOWN_RE.match(stripped)
    if m:
        return m.group(2).strip()
    return stripped


def sectionize(extraction: ExtractionRecord) -> list[SectionRecord]:
    sections: list[SectionRecord] = []
    heading_stack: list[tuple[int, str]] = []
    current_lines: list[LineRecord] = []
    current_heading: tuple[int, str] | None = None

    def flush() -> None:
        nonlocal current_lines, current_heading
        if not current_lines:
            current_heading = None
            return
        section_id = f"S{len(sections) + 1:04d}"
        relevant = [line for line in current_lines if line.text or line.text == ""]
        if not relevant:
            current_lines = []
            current_heading = None
            return
        start_line = relevant[0].line_no
        end_line = relevant[-1].line_no
        page_values = [line.page_no for line in relevant if line.page_no is not None]
        paragraph_values = [line.paragraph_no for line in relevant if line.paragraph_no is not None]
        heading_path = [name for _, name in heading_stack]
        heading_text = current_heading[1] if current_heading else (heading_path[-1] if heading_path else None)
        normalized_text = "\n".join(line.text for line in relevant).rstrip("\n")
        sections.append(
            SectionRecord(
                section_id=section_id,
                heading=heading_text,
                heading_path=heading_path,
                heading_level=current_heading[0] if current_heading else (len(heading_path) or 0),
                start_line=start_line,
                end_line=end_line,
                start_page=min(page_values) if page_values else None,
                end_page=max(page_values) if page_values else None,
                start_paragraph=min(paragraph_values) if paragraph_values else None,
                end_paragraph=max(paragraph_values) if paragraph_values else None,
                normalized_text=normalized_text,
                normalized_sha256=sha256_text(normalized_text),
                token_estimate=estimate_tokens(normalized_text),
            )
        )
        current_lines = []
        current_heading = None

    for line in extraction.lines:
        if is_heading_line(line):
            flush()
            level = heading_level(line)
            heading_text = normalize_heading_text(line.text)
            if level <= 0:
                level = len(heading_stack) + 1 if heading_stack else 1
            while heading_stack and heading_stack[-1][0] >= level:
                heading_stack.pop()
            heading_stack.append((level, heading_text))
            current_heading = (level, heading_text)
        current_lines.append(line)

    flush()
    return sections


def normalize_heading_text(text: str) -> str:
    stripped = clean_heading(text)
    if stripped.startswith("Phase "):
        return re.sub(r"\s+", " ", stripped)
    return re.sub(r"\s+", " ", stripped).strip()


def estimate_tokens(text: str) -> int:
    if not text:
        return 0
    return max(1, int((len(text.encode("utf-8")) + 3) // 4))


def source_fingerprint(entries: list[ManifestEntry]) -> str:
    payload = [
        {
            "source_id": entry.source_id,
            "sha256": entry.sha256,
            "size_bytes": entry.size_bytes,
            "family_key": entry.family_key,
            "version_key": entry.version_key,
        }
        for entry in entries
    ]
    return fingerprint({"schema_version": SCHEMA_VERSION, "sources": payload})


def env_fingerprint(extra_names: Iterable[str] | None = None) -> dict[str, str]:
    names = {
        "PATH",
        "HOME",
        "PWD",
        "TMPDIR",
        "LANG",
        "LC_ALL",
        "NO_COLOR",
        "CI",
        "JAVA_HOME",
        "KOTLIN_HOME",
        "GRADLE_USER_HOME",
        "TERMUX_PREFIX",
        "TERMUX_VERSION",
        "ANDROID_ROOT",
        "ANDROID_DATA",
    }
    if extra_names:
        names.update(extra_names)
    snapshot: dict[str, str] = {}
    for name in sorted(names):
        if name not in os.environ:
            continue
        value = os.environ[name]
        snapshot[name] = "<redacted>" if SECRET_NAME_RE.search(name) else value
    for name, value in os.environ.items():
        if not name.startswith("ATROPOS_"):
            continue
        snapshot[name] = "<redacted>" if SECRET_NAME_RE.search(name) else value
    return snapshot


def command_file_paths(argv: list[str], cwd: Path | None = None) -> list[Path]:
    cwd = cwd or Path.cwd()
    paths: list[Path] = []
    for token in argv:
        if token in {"--", "-c", "&&", "||"}:
            continue
        if token.startswith("-"):
            continue
        candidate = Path(token)
        if candidate.is_absolute() and candidate.exists():
            paths.append(candidate)
            continue
        relative = cwd / token
        if relative.exists():
            paths.append(relative)
    unique: list[Path] = []
    seen: set[Path] = set()
    for path in paths:
        resolved = path.resolve()
        if resolved in seen:
            continue
        seen.add(resolved)
        unique.append(resolved)
    return unique


def combined_file_fingerprint(paths: Iterable[Path]) -> dict[str, Any]:
    records = []
    for path in sorted({p.resolve() for p in paths}):
        if path.is_file():
            records.append(
                {
                    "path": str(path),
                    "sha256": sha256_file(path),
                    "size_bytes": path.stat().st_size,
                }
            )
        elif path.is_dir():
            child_files = sorted(p for p in path.rglob("*") if p.is_file())
            records.append(
                {
                    "path": str(path),
                    "sha256": fingerprint(
                        {
                            "kind": "dir",
                            "children": [
                                {
                                    "path": str(child.relative_to(path)),
                                    "sha256": sha256_file(child),
                                    "size_bytes": child.stat().st_size,
                                }
                                for child in child_files
                            ],
                        }
                    ),
                    "size_bytes": sum(child.stat().st_size for child in child_files),
                }
            )
    return {"paths": records, "fingerprint": fingerprint(records)}


def select_repo_files(patterns: Iterable[str]) -> list[Path]:
    files: list[Path] = []
    for pattern in patterns:
        for path in sorted(REPO_ROOT.glob(pattern)):
            if path.is_file():
                files.append(path.resolve())
    unique: list[Path] = []
    seen: set[Path] = set()
    for path in files:
        if path in seen:
            continue
        seen.add(path)
        unique.append(path)
    return unique


def load_json(path: Path, default: Any = None) -> Any:
    if not path.exists():
        return default
    return json.loads(path.read_text("utf-8"))


def read_text(path: Path) -> str:
    return path.read_text("utf-8")


def write_lines(path: Path, lines: Iterable[str]) -> None:
    atomic_write_text(path, "\n".join(lines) + "\n")


def shell_quote(value: str) -> str:
    import shlex

    return shlex.quote(value)


def json_line(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


def to_jsonable(value: Any) -> Any:
    if dataclasses.is_dataclass(value):
        return dataclasses.asdict(value)
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, dict):
        return {str(k): to_jsonable(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [to_jsonable(v) for v in value]
    return value


def cache_path(root: Path, namespace: str, key: str, suffix: str = ".json") -> Path:
    safe = key[:2] if key else "__"
    return root / namespace / safe / f"{key}{suffix}"


def hashed_cache_path(root: Path, namespace: str, key: str, suffix: str = ".json") -> Path:
    digest = sha256_text(key)
    return root / namespace / digest[:2] / f"{digest}{suffix}"


def write_atomic_json(path: Path, data: Any) -> None:
    atomic_write_json(path, to_jsonable(data))


def read_json(path: Path, default: Any = None) -> Any:
    if not path.exists():
        return default
    return json.loads(path.read_text("utf-8"))


def load_index(index_root: str | Path | None = None) -> dict[str, Any]:
    root = find_index_root(str(index_root) if index_root is not None else None)
    index_path = root / "index.json"
    if not index_path.exists():
        raise FileNotFoundError(f"missing index: {index_path}")
    return read_json(index_path)


def indexed_lines_from_text(text: str, kind: str) -> list[LineRecord]:
    if kind == "pdf":
        return extract_pdf_lines(text)
    return extract_plain_lines(text, kind=kind)


def slice_indexed_lines(text: str, kind: str, start_line: int, end_line: int) -> list[LineRecord]:
    lines = indexed_lines_from_text(text, kind)
    start = max(1, start_line)
    end = max(start, end_line)
    return lines[start - 1 : end]


def find_index_root(explicit: str | None = None) -> Path:
    if explicit:
        return Path(explicit)
    return CONTEXT_CACHE_ROOT / "source-index" / f"v{SCHEMA_VERSION}"


def source_cache_root() -> Path:
    return ensure_dir(CONTEXT_CACHE_ROOT / "source-extract" / f"v{SCHEMA_VERSION}")


def query_cache_root() -> Path:
    return ensure_dir(CONTEXT_CACHE_ROOT / "source-query" / f"v{SCHEMA_VERSION}")


def pack_cache_root() -> Path:
    return ensure_dir(CONTEXT_CACHE_ROOT / "context-pack" / f"v{SCHEMA_VERSION}")


def gate_cache_root() -> Path:
    return ensure_dir(GATE_CACHE_ROOT / f"v{SCHEMA_VERSION}")


def duplicate_groups(entries: list[ManifestEntry]) -> dict[str, list[ManifestEntry]]:
    groups: dict[str, list[ManifestEntry]] = {}
    for entry in entries:
        groups.setdefault(entry.sha256, []).append(entry)
    return {sha: members for sha, members in groups.items() if len(members) > 1}


def supersession_groups(entries: list[ManifestEntry]) -> dict[str, list[ManifestEntry]]:
    groups: dict[str, list[ManifestEntry]] = {}
    for entry in entries:
        if entry.version_key == "unversioned":
            continue
        groups.setdefault(entry.family_key, []).append(entry)
    out: dict[str, list[ManifestEntry]] = {}
    for family, members in groups.items():
        if len(members) > 1:
            out[family] = members
    return out


def sort_entries(entries: list[ManifestEntry]) -> list[ManifestEntry]:
    return sorted(entries, key=lambda e: (e.family_key.lower(), e.version_key.lower(), e.source_id))


def abbreviate(text: str, limit: int = 220) -> str:
    collapsed = re.sub(r"\s+", " ", text.strip())
    if len(collapsed) <= limit:
        return collapsed
    return collapsed[: max(0, limit - 1)].rstrip() + "…"


def match_terms(text: str, terms: list[str]) -> list[str]:
    lowered = text.lower()
    reasons: list[str] = []
    for term in terms:
        if not term:
            continue
        if term.lower() in lowered:
            reasons.append(term)
    return reasons


def source_label(entry: ManifestEntry) -> str:
    return f"{entry.source_id}::{entry.original_filename}"
