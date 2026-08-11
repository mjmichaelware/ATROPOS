"use client";

import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/status-badge";
import { filterFindings, findingCounts, findingFocusNodeId, type FindingFilter } from "@/lib/planning/findings";
import type { PlanFinding } from "@/lib/planning/schemas";
import { findingSeverityTone, normalizeFindingSeverity } from "@/lib/planning/status";

export function FindingsList({
  findings,
  filter,
  onFilterChange,
  loadedNodeIds,
  onFocusNode,
}: {
  findings: PlanFinding[];
  filter: FindingFilter;
  onFilterChange: (filter: FindingFilter) => void;
  loadedNodeIds: ReadonlySet<string>;
  onFocusNode: (nodeId: string) => void;
}) {
  const counts = findingCounts(findings);
  const filtered = filterFindings(findings, filter);
  return (
    <div className="sg-findings-list">
      <div className="sg-graph-command-group" role="group" aria-label="Finding severity filter">
        <Button type="button" variant={filter === "all" ? "verified" : "quiet"} aria-pressed={filter === "all"} onClick={() => onFilterChange("all")}>
          All ({counts.total})
        </Button>
        <Button type="button" variant={filter === "ERROR" ? "verified" : "quiet"} aria-pressed={filter === "ERROR"} onClick={() => onFilterChange("ERROR")}>
          Errors ({counts.error})
        </Button>
        <Button type="button" variant={filter === "WARNING" ? "verified" : "quiet"} aria-pressed={filter === "WARNING"} onClick={() => onFilterChange("WARNING")}>
          Warnings ({counts.warning})
        </Button>
      </div>
      {filtered.length === 0 ? (
        <p role="status">No findings match this filter.</p>
      ) : (
        <ul aria-label="Plan verification findings">
          {filtered.map((finding) => {
            const severity = normalizeFindingSeverity(finding.severity);
            const focusId = findingFocusNodeId(finding, loadedNodeIds);
            return (
              <li key={finding.id}>
                <StatusBadge tone={findingSeverityTone(severity)} label={severity} />
                <span className="sg-mono">{finding.code}</span>
                <p>{finding.message}</p>
                {focusId ? (
                  <Button type="button" variant="quiet" onClick={() => onFocusNode(focusId)}>
                    Focus in graph
                  </Button>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
