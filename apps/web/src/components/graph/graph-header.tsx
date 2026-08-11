"use client";

import { Button } from "@/components/ui/button";
import { SegmentedControl } from "@/components/ui/segmented-control";
import type { GraphMode } from "@/lib/graph/schemas";
import type { GraphSizeTier } from "@/lib/graph/zoom";

export function GraphHeader({
  projectName,
  mode,
  onModeChange,
  nodeCount,
  edgeCount,
  size,
  isFetching,
  onRefresh,
}: {
  projectName: string;
  mode: GraphMode;
  onModeChange: (mode: GraphMode) => void;
  nodeCount: number;
  edgeCount: number;
  size: GraphSizeTier;
  isFetching: boolean;
  onRefresh: () => void;
}) {
  return (
    <header className="sg-source-hero sg-graph-hero">
      <p className="sg-micro-label">Graph foundation</p>
      <h1>{projectName}</h1>
      <p>
        {mode === "authority" ? "Authority mode: cycles are legal and representable." : "Execution mode: cycles are rejected before persistence."} {nodeCount} node
        {nodeCount === 1 ? "" : "s"}, {edgeCount} edge{edgeCount === 1 ? "" : "s"} loaded ({size} scale).
      </p>
      <div className="sg-graph-hero-controls">
        <SegmentedControl
          label="Graph mode"
          value={mode}
          onChange={onModeChange}
          options={[
            { value: "authority", label: "Authority" },
            { value: "execution", label: "Execution" },
          ]}
        />
        <Button type="button" variant="secondary" loading={isFetching} onClick={onRefresh}>
          Refresh
        </Button>
      </div>
    </header>
  );
}
