import { StatusBadge } from "@/components/ui/status-badge";
import type { HandoffWorkspace } from "@/lib/handoff/schemas";

export function HandoffOverview({ workspace }: { workspace: HandoffWorkspace | undefined }) {
  if (!workspace) {
    return <p className="sg-muted">Handoff summary is unavailable.</p>;
  }
  const counts = workspace.counts ?? {};
  return (
    <div className="sg-planning-overview">
      <dl>
        <div>
          <dt>Bindings</dt>
          <dd>{counts.bindings ?? "unknown"} ({counts.enabled_bindings ?? 0} enabled)</dd>
        </div>
        <div>
          <dt>Exports</dt>
          <dd>{counts.exports ?? "unknown"} ({counts.verified_exports ?? 0} verified, {counts.invalid_exports ?? 0} invalid)</dd>
        </div>
        <div>
          <dt>Execution runs</dt>
          <dd>{counts.execution_runs ?? "unknown"} ({counts.verified_execution_runs ?? 0} verified, {counts.rejected_execution_runs ?? 0} rejected)</dd>
        </div>
        <div>
          <dt>Receipts / findings</dt>
          <dd>{counts.receipts ?? "unknown"} receipts, {counts.execution_findings ?? "unknown"} findings</dd>
        </div>
        <div>
          <dt>Providers / renderers</dt>
          <dd>{counts.providers ?? "unknown"} providers ({counts.ready_providers ?? 0} ready), {counts.renderers ?? "unknown"} renderers ({counts.enabled_renderers ?? 0} enabled)</dd>
        </div>
      </dl>
      {workspace.latest_export ? (
        <p>
          Latest export: <span className="sg-mono">{String(workspace.latest_export.id).slice(0, 8)}</span>{" "}
          <StatusBadge tone="neutral" label={String(workspace.latest_export.status ?? "UNKNOWN")} />
        </p>
      ) : (
        <p className="sg-muted">No export has been generated yet.</p>
      )}
      {workspace.latest_execution_run ? (
        <p>
          Latest execution run: <span className="sg-mono">{String(workspace.latest_execution_run.id).slice(0, 8)}</span>{" "}
          <StatusBadge tone="neutral" label={String(workspace.latest_execution_run.status ?? "UNKNOWN")} />
        </p>
      ) : (
        <p className="sg-muted">No execution run has started yet.</p>
      )}
    </div>
  );
}
