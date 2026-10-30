import { StatusBadge } from "@/components/ui/status-badge";
import { normalizePlanStatus, planStatusTone } from "@/lib/planning/status";

export function PlanStatusBadge({ status }: { status: unknown }) {
  const normalized = normalizePlanStatus(status);
  return <StatusBadge tone={planStatusTone(normalized)} label={normalized} />;
}
