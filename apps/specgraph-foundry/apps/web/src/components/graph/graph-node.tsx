"use client";

import { Handle, Position, type NodeProps } from "@xyflow/react";
import { memo } from "react";
import type { RendererNodeData } from "@/lib/graph/transform";
import type { ZoomTier } from "@/lib/graph/zoom";

export type SgFlowNodeData = RendererNodeData & {
  tier: ZoomTier;
  isSelected: boolean;
};

function GraphNodeImpl({ data }: NodeProps & { data: SgFlowNodeData }) {
  const { category, label, typeLabel, status, tier, isSelected } = data;
  const showLabel = tier !== "constellation";
  const showDetail = tier === "inspection";
  return (
    <div
      className="sg-graph-node"
      data-category={category}
      data-status={(status ?? "UNKNOWN").toLowerCase()}
      data-selected={isSelected || undefined}
      data-tier={tier}
      role="img"
      aria-label={`${typeLabel} node: ${label}${status ? `, status ${status}` : ""}`}
      tabIndex={-1}
    >
      <Handle type="target" position={Position.Top} className="sg-graph-handle" />
      <span className="sg-graph-node-mark" aria-hidden="true" />
      {showLabel ? <span className="sg-graph-node-label">{label}</span> : null}
      {showDetail ? <span className="sg-graph-node-type">{typeLabel}</span> : null}
      <Handle type="source" position={Position.Bottom} className="sg-graph-handle" />
    </div>
  );
}

export const GraphNode = memo(GraphNodeImpl);
