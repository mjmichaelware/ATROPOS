import { StatusBadge } from "@/components/ui/status-badge";
import { isServerReadyExecutionNode, normalizeNodeStatus, stageOf } from "@/lib/execution/status";
import type { ExecutionRunNode, FreeformRecord } from "@/lib/execution/schemas";

export function ExecutionNodeList({ nodes, readyNodes }: { nodes: ExecutionRunNode[]; readyNodes: FreeformRecord[] | undefined }) {
  if (nodes.length === 0) {
    return <p className="sg-muted">No execution nodes are recorded for this run.</p>;
  }
  return (
    <table className="sg-graph-table" aria-label="Execution nodes">
      <thead>
        <tr>
          <th scope="col">Stage</th>
          <th scope="col">Title</th>
          <th scope="col">Status</th>
          <th scope="col">Server-ready</th>
          <th scope="col">Lease</th>
        </tr>
      </thead>
      <tbody>
        {nodes.map((node) => {
          const ready = isServerReadyExecutionNode(node.id, readyNodes);
          const status = normalizeNodeStatus(node.status);
          return (
            <tr key={node.id}>
              <th scope="row">{stageOf(node.stage)}</th>
              <td>{node.title ?? "Untitled node"}</td>
              <td>
                <StatusBadge tone={status === "BLOCKED" || status === "FAILED" ? "danger" : status === "COMPLETE" ? "success" : "neutral"} label={status} />
              </td>
              <td>{ready ? <StatusBadge tone="success" label="Ready" /> : "—"}</td>
              <td>{node.lease_owner ? <span className="sg-mono">{node.lease_owner}</span> : "—"}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
