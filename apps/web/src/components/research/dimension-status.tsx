import Link from "next/link";
import type { Route } from "next";
import { StatusBadge } from "@/components/ui/status-badge";
import { dimensionStatusLabel, normalizeDimensionStatus } from "@/lib/research/status";

export function DimensionStatus({ status, href, linkLabel }: { status?: unknown; href?: Route; linkLabel?: string }) {
  const normalized = normalizeDimensionStatus(status);
  const tone = normalized === "RESOLVED" ? "success" : normalized === "NOT_APPLICABLE" ? "neutral" : normalized === "OPEN" ? "warning" : "info";
  const label = dimensionStatusLabel(normalized);
  const badge = <StatusBadge tone={tone} label={label} />;
  return href ? (
    <Link href={href} aria-label={linkLabel ? `${linkLabel}: ${label}` : undefined}>
      {badge}
    </Link>
  ) : (
    badge
  );
}
