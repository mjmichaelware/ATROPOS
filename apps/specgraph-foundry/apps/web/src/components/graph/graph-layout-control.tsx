"use client";

import { SegmentedControl } from "@/components/ui/segmented-control";
import type { LayoutAlgorithm } from "@/lib/graph/schemas";

const DESCRIPTIONS: Record<LayoutAlgorithm, string> = {
  blueprint: "Layered, directional layout for reading dependencies top-to-bottom.",
  compact: "Denser layout for overview on limited screen space.",
  freeform: "Manual positioning. Visual only — relations, readiness, and verification are unchanged.",
  focus: "Selected node and its loaded neighborhood only.",
};

export function GraphLayoutControl({ value, onChange, hasSelection }: { value: LayoutAlgorithm; onChange: (value: LayoutAlgorithm) => void; hasSelection: boolean }) {
  return (
    <div className="sg-graph-layout-control">
      <SegmentedControl
        label="Layout mode"
        value={value}
        onChange={onChange}
        options={[
          { value: "blueprint", label: "Blueprint" },
          { value: "compact", label: "Compact" },
          { value: "freeform", label: "Freeform" },
          { value: "focus", label: hasSelection ? "Focus" : "Focus (select a node)" },
        ]}
      />
      <p className="sg-micro-label" role="status">
        {DESCRIPTIONS[value]}
      </p>
      {value === "freeform" ? <p className="sg-muted">Manual positions are saved to this device only. No server layout-persistence endpoint exists yet.</p> : null}
    </div>
  );
}
