"use client";

import { BaseEdge, EdgeLabelRenderer, getSmoothStepPath, type EdgeProps } from "@xyflow/react";
import { memo } from "react";
import type { RendererEdgeData } from "@/lib/graph/transform";

export type SgFlowEdgeData = RendererEdgeData & {
  showLabel: boolean;
};

function GraphEdgeImpl({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data, selected }: EdgeProps & { data: SgFlowEdgeData }) {
  const [path, labelX, labelY] = getSmoothStepPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition, borderRadius: 8 });
  const category = data.category;
  const label = data.relationLabel;
  return (
    <>
      <BaseEdge
        id={id}
        path={path}
        className="sg-graph-edge"
        data-category={category}
        data-inferred={data.inferred || undefined}
        data-selected={selected || undefined}
        markerEnd={`url(#sg-graph-arrow-${category})`}
      />
      {data.showLabel && label ? (
        <EdgeLabelRenderer>
          <div
            className="sg-graph-edge-label"
            style={{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }}
            data-category={category}
          >
            {label}
          </div>
        </EdgeLabelRenderer>
      ) : null}
    </>
  );
}

export const GraphEdge = memo(GraphEdgeImpl);
