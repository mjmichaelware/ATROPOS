import type { FindingSeverity, PlanFinding } from "./schemas";
import { normalizeFindingSeverity } from "./status";

export type FindingFilter = "all" | "ERROR" | "WARNING" | "INFO";

export function filterFindings(findings: PlanFinding[], filter: FindingFilter): PlanFinding[] {
  if (filter === "all") return findings;
  return findings.filter((finding) => normalizeFindingSeverity(finding.severity) === filter);
}

export function groupFindingsBySeverity(findings: PlanFinding[]): Record<FindingSeverity, PlanFinding[]> {
  const groups: Record<FindingSeverity, PlanFinding[]> = { ERROR: [], WARNING: [], INFO: [], UNKNOWN: [] };
  for (const finding of findings) {
    groups[normalizeFindingSeverity(finding.severity)].push(finding);
  }
  return groups;
}

export function findingCounts(findings: PlanFinding[]) {
  const groups = groupFindingsBySeverity(findings);
  return {
    error: groups.ERROR.length,
    warning: groups.WARNING.length,
    info: groups.INFO.length,
    unknown: groups.UNKNOWN.length,
    total: findings.length,
  };
}

/**
 * Only returns a focusable node ID when the finding's entity_id is actually
 * present among currently loaded graph node IDs. Findings may reference
 * entities that are not node IDs (plans, bindings, atoms); those are never
 * assumed to be focusable graph nodes.
 */
export function findingFocusNodeId(finding: PlanFinding, loadedNodeIds: ReadonlySet<string>): string | undefined {
  if (!finding.entity_id) return undefined;
  return loadedNodeIds.has(finding.entity_id) ? finding.entity_id : undefined;
}
