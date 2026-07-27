import { StatusBadge } from "@/components/ui/status-badge";
import { dimensionStatusLabel, normalizeDimensionStatus } from "@/lib/research/status";

export function DimensionStatus({ status }: { status?: unknown }) {
  const normalized = normalizeDimensionStatus(status);
  const tone = normalized === "RESOLVED" ? "success" : normalized === "NOT_APPLICABLE" ? "neutral" : normalized === "OPEN" ? "warning" : "info";
  return <StatusBadge tone={tone} label={dimensionStatusLabel(normalized)} />;
}
