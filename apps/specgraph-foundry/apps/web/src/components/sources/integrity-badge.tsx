export function IntegrityBadge({ state = "unknown" }: { state?: string }) {
  const normalized = state.toLowerCase();
  const tone = normalized.includes("verified") || normalized.includes("finalized") || normalized.includes("complete") ? "verified" : normalized.includes("fail") ? "failed" : "pending";
  return <span className="sg-integrity-badge" data-tone={tone}>{state}</span>;
}
