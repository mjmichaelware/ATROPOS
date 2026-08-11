#!/usr/bin/env python3
"""Append a timestamped phase/checkpoint progress snapshot.

The binary obligation auditor remains the sole completion calculator. This
wrapper only persists its result longitudinally and renders a wide history
table for human review.
"""

from __future__ import annotations

import csv
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMPLETION = ROOT / "docs/completion"
AUDITOR = ROOT / "scripts/audit-code-completion.py"
REGISTRY = COMPLETION / "ATROPOS_CODE_COMPLETION_BASELINE.json"
HISTORY = COMPLETION / "ATROPOS_PHASE_PROGRESS_HISTORY.tsv"
SNAPSHOT = COMPLETION / "ATROPOS_PHASE_PROGRESS_SNAPSHOT.md"

LEGACY = {
    "Phase 0": 75.0, "Phase 1": 80.0, "Phase 2": 70.0, "Phase 3": 75.0,
    "Phase 4": 85.0, "Phase 5": 65.0, "Phase 6": 80.0, "Phase 7": 50.0,
    "Phase 8": 80.0, "Phase 9": 60.0, "Phase 10": 55.0, "Phase 11": 65.0,
    "Phase 12": 40.0, "Phase 13": 70.0, "Phase 14": 35.0, "Phase 15": 40.0,
    "Phase 16": 30.0, "Phase 17": 25.0, "Phase 18": 20.0,
    "Phase 19": 20.0, "Phase 20": 12.0,
    "Checkpoint 1": 70.0, "Checkpoint 2": 43.0, "Checkpoint 3": 22.0,
    "Checkpoint 4": 12.0, "Overall": 42.0,
}

FIELDS = [
    "timestamp", "metric", "scope", "numerator", "denominator", "percentage",
    "prior_percentage", "delta_pp", "evidence", "head",
]


def pct(numerator: int, denominator: int) -> float:
    return round(numerator * 100.0 / denominator, 4) if denominator else 0.0


def current_rows(data: dict) -> list[dict[str, str]]:
    stamp = data["generatedAt"]
    head = data["currentHead"]
    rows: list[dict[str, str]] = []
    scopes = {f"Phase {phase}": values for phase, values in data["perPhase"].items()}
    groups = {
        "Checkpoint 1": list(range(0, 12)),
        "Checkpoint 2": list(range(12, 17)),
        "Checkpoint 3": list(range(17, 20)),
        "Checkpoint 4": [20],
    }
    for name, phases in groups.items():
        total = sum(data["perPhase"][str(phase)]["total"] for phase in phases)
        written = sum(data["perPhase"][str(phase)]["currentWritten"] for phase in phases)
        scopes[name] = {"total": total, "currentWritten": written}
    scopes["Overall"] = {
        "total": data["totalObligations"], "currentWritten": data["currentWritten"]
    }
    for scope, values in scopes.items():
        rows.append({
            "timestamp": stamp,
            "metric": "code-base-obligation",
            "scope": scope,
            "numerator": str(values["currentWritten"]),
            "denominator": str(values["total"]),
            "percentage": f"{pct(values['currentWritten'], values['total']):.4f}",
            "prior_percentage": "",
            "delta_pp": "",
            "evidence": "ATROPOS_CODE_COMPLETION_REPORT.md",
            "head": head,
        })
    return rows


def legacy_rows() -> list[dict[str, str]]:
    return [{
        "timestamp": "2026-07-29T00:00:00Z",
        "metric": "legacy-mixed-baseline",
        "scope": scope,
        "numerator": "",
        "denominator": "",
        "percentage": f"{value:.4f}",
        "prior_percentage": "",
        "delta_pp": "",
        "evidence": "AGENTS.md locked original baseline; not comparable to code-base-obligation",
        "head": "",
    } for scope, value in LEGACY.items()]


def read_rows() -> list[dict[str, str]]:
    if not HISTORY.exists():
        return []
    with HISTORY.open(newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def write_history(rows: list[dict[str, str]]) -> None:
    HISTORY.parent.mkdir(parents=True, exist_ok=True)
    with HISTORY.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS, delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)


def write_snapshot(rows: list[dict[str, str]]) -> None:
    timestamps: list[str] = []
    for row in rows:
        label = f"{row['timestamp']} {row['metric']}"
        if label not in timestamps:
            timestamps.append(label)
    scopes = sorted({row["scope"] for row in rows}, key=lambda value: (value.startswith("Checkpoint") is False, value))
    by_key = {(row["scope"], f"{row['timestamp']} {row['metric']}"): row for row in rows}
    lines = [
        "# ATROPOS Phase Progress Snapshot",
        "",
        "This is append-only longitudinal accounting. The locked legacy baseline is shown separately from the binary code-base obligation metric; percentages across those metrics are not directly comparable.",
        "",
        "| Scope | " + " | ".join(timestamps) + " | Latest prior -> latest |",
        "|---|" + "---|" * (len(timestamps) + 1),
    ]
    for scope in scopes:
        values = []
        for timestamp in timestamps:
            row = by_key.get((scope, timestamp))
            values.append(f"{row['percentage']}%" if row else "")
        same_metric_values = []
        latest_metric = None
        for timestamp in reversed(timestamps):
            row = by_key.get((scope, timestamp))
            if row and latest_metric is None:
                latest_metric = row["metric"]
            if row and row["metric"] == latest_metric:
                same_metric_values.append(float(row["percentage"]))
        if len(same_metric_values) >= 2:
            transition = f"{same_metric_values[1]:.4f}% -> {same_metric_values[0]:.4f}%"
        elif latest_metric == "code-base-obligation" and any(
            by_key.get((scope, timestamp), {}).get("metric") == "legacy-mixed-baseline" for timestamp in timestamps
        ):
            transition = "not comparable to legacy metric"
        else:
            transition = "initial snapshot"
        lines.append("| " + scope + " | " + " | ".join(values) + " | " + transition + " |")
    lines += ["", "Evidence: `ATROPOS_CODE_COMPLETION_REPORT.md`, `ATROPOS_CODE_COMPLETION_BASELINE.json`, and `ATROPOS_PHASE_PROGRESS_HISTORY.tsv`."]
    SNAPSHOT.write_text("\n".join(lines) + "\n")


def main() -> int:
    force = "--force" in sys.argv
    subprocess.run([sys.executable, str(AUDITOR)], cwd=ROOT, check=True)
    data = json.loads(REGISTRY.read_text())
    rows = read_rows()
    if not rows:
        rows.extend(legacy_rows())
    elif not any(row["metric"] == "legacy-mixed-baseline" and row["scope"] == "Overall" for row in rows):
        rows.append(next(row for row in legacy_rows() if row["scope"] == "Overall"))
    fresh = current_rows(data)
    current_stamp = data["generatedAt"]
    current_head = data["currentHead"]
    already_audited_head = any(
        row["metric"] == "code-base-obligation" and row["head"] == current_head for row in rows
    )
    if already_audited_head and not force:
        fresh = []
    rows = [row for row in rows if not (row["timestamp"] == current_stamp and row["metric"] == "code-base-obligation")]
    prior = {row["scope"]: row for row in rows if row["metric"] == "code-base-obligation"}
    for row in fresh:
        old = prior.get(row["scope"])
        if old:
            row["prior_percentage"] = old["percentage"]
            row["delta_pp"] = f"{float(row['percentage']) - float(old['percentage']):+.4f}"
    rows.extend(fresh)
    write_history(rows)
    write_snapshot(rows)
    print(json.dumps({"snapshot": str(SNAPSHOT), "history": str(HISTORY), "rows": len(rows), "generatedAt": current_stamp}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
