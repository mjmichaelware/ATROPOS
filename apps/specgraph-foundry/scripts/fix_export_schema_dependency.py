from pathlib import Path

ROOT = Path.cwd()

if ROOT.name != "specgraph-foundry" or not (ROOT / ".git").is_dir():
    raise SystemExit(f"Wrong repository: {ROOT}")

target = ROOT / "src/specgraph_foundry/exports.py"
content = target.read_text(encoding="utf-8")

import_marker = "from .planning import PlanningService\n"
import_line = "from .research import ResearchService\n"

if import_line not in content:
    if import_marker not in content:
        raise SystemExit(
            "PlanningService import marker not found"
        )

    content = content.replace(
        import_marker,
        import_marker + import_line,
        1,
    )

service_marker = """        self.database = database
        self.planning = PlanningService(
            database
        )
"""

service_replacement = """        self.database = database
        self.research = ResearchService(
            database
        )
        self.planning = PlanningService(
            database
        )
"""

if "self.research = ResearchService(" not in content:
    if service_marker not in content:
        raise SystemExit(
            "ExportService initialization marker not found"
        )

    content = content.replace(
        service_marker,
        service_replacement,
        1,
    )

target.write_text(
    content,
    encoding="utf-8",
)

print("FIXED export research-schema dependency")
