import type { DimensionStatusValue, ResearchCounts } from "./schemas";

export function normalizeDimensionStatus(value: unknown): DimensionStatusValue {
  const status = String(value ?? "UNKNOWN").toUpperCase();
  if (status === "RESOLVED" || status === "NOT_APPLICABLE" || status === "OPEN") {
    return status;
  }
  return "UNKNOWN";
}

export function dimensionStatusLabel(value: unknown) {
  const status = normalizeDimensionStatus(value);
  return {
    OPEN: "Open",
    RESOLVED: "Resolved",
    NOT_APPLICABLE: "Not applicable",
    UNKNOWN: "Unknown",
  }[status];
}

export function taskStatusTone(value: unknown): "info" | "success" | "warning" | "danger" | "neutral" {
  const status = String(value ?? "UNKNOWN").toUpperCase();
  if (status === "COMPLETE" || status === "COMPLETED" || status === "SUCCEEDED") return "success";
  if (status === "FAILED" || status === "TIMED_OUT") return "danger";
  if (status === "CLAIMED" || status === "RUNNING") return "info";
  if (status === "PENDING" || status === "QUEUED") return "warning";
  return "neutral";
}

export function completionRatio(counts?: ResearchCounts) {
  const open = numberValue(counts?.open_dimensions);
  const resolved = numberValue(counts?.resolved_dimensions);
  const notApplicable = numberValue(counts?.not_applicable_dimensions);
  const total = open + resolved + notApplicable;
  return total > 0 ? Math.round(((resolved + notApplicable) / total) * 100) : 0;
}

export function numberValue(value: unknown) {
  const numeric = Number(value ?? 0);
  return Number.isFinite(numeric) && numeric > 0 ? numeric : 0;
}
