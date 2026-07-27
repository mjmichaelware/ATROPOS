"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/status-badge";
import type { ExecutionFinding } from "@/lib/execution/schemas";

type Filter = "all" | "ERROR" | "WARNING";

function severity(value: unknown): "ERROR" | "WARNING" | "INFO" | "UNKNOWN" {
  const normalized = String(value ?? "UNKNOWN").toUpperCase();
  return normalized === "ERROR" || normalized === "WARNING" || normalized === "INFO" ? normalized : "UNKNOWN";
}

export function ExecutionFindingsList({ findings }: { findings: ExecutionFinding[] }) {
  const [filter, setFilter] = useState<Filter>("all");
  const errorCount = findings.filter((finding) => severity(finding.severity) === "ERROR").length;
  const warningCount = findings.filter((finding) => severity(finding.severity) === "WARNING").length;
  const filtered = filter === "all" ? findings : findings.filter((finding) => severity(finding.severity) === filter);

  return (
    <div className="sg-findings-list">
      <p className="sg-muted">
        Findings are what independent verification actually caught — a mismatch, a missing dependency, a receipt that didn&apos;t validate. An Error blocks the run from counting as verified; a
        Warning doesn&apos;t block it but is worth a look.
      </p>
      <div className="sg-graph-command-group" role="group" aria-label="Execution finding severity filter">
        <Button type="button" variant={filter === "all" ? "verified" : "quiet"} aria-pressed={filter === "all"} onClick={() => setFilter("all")}>
          All ({findings.length})
        </Button>
        <Button type="button" variant={filter === "ERROR" ? "verified" : "quiet"} aria-pressed={filter === "ERROR"} onClick={() => setFilter("ERROR")}>
          Errors ({errorCount})
        </Button>
        <Button type="button" variant={filter === "WARNING" ? "verified" : "quiet"} aria-pressed={filter === "WARNING"} onClick={() => setFilter("WARNING")}>
          Warnings ({warningCount})
        </Button>
      </div>
      {filtered.length === 0 ? (
        <p role="status">No findings match this filter.</p>
      ) : (
        <ul aria-label="Execution validation findings">
          {filtered.map((finding) => (
            <li key={finding.id}>
              <StatusBadge tone={severity(finding.severity) === "ERROR" ? "danger" : "warning"} label={severity(finding.severity)} />
              <span className="sg-mono">{finding.code}</span>
              <p>{finding.message}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
