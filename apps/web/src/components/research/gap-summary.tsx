import { MetricOrbit } from "@/components/visual/metric-orbit";
import type { GapMatrix, ResearchCounts } from "@/lib/research/schemas";
import { completionRatio, numberValue } from "@/lib/research/status";
import { countStatuses } from "@/lib/research/gaps";

export function GapSummary({ counts, matrix }: { counts?: ResearchCounts; matrix?: GapMatrix }) {
  const derived = countStatuses(matrix);
  const open = numberValue(counts?.open_dimensions) || derived.open;
  const resolved = numberValue(counts?.resolved_dimensions) || derived.resolved;
  const notApplicable = numberValue(counts?.not_applicable_dimensions) || derived.notApplicable;
  return (
    <div className="sg-research-summary" aria-label="Research gap summary">
      <MetricOrbit label="Closed dimensions" value={completionRatio(counts ?? { open_dimensions: open, resolved_dimensions: resolved, not_applicable_dimensions: notApplicable })} />
      <dl>
        <div><dt>Open</dt><dd>{open}</dd></div>
        <div><dt>Resolved</dt><dd>{resolved}</dd></div>
        <div><dt>Not applicable</dt><dd>{notApplicable}</dd></div>
      </dl>
    </div>
  );
}
