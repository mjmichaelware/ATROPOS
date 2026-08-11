"use client";

import { Button } from "@/components/ui/button";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Tooltip } from "@/components/ui/tooltip";
import { CommandDock } from "@/components/visual/command-dock";
import type { GraphView } from "@/lib/graph/url-state";

export function GraphCommandDock({
  view,
  onViewChange,
  hasSelection,
  onFitGraph,
  onFitSelection,
  onZoomIn,
  onZoomOut,
  onResetView,
}: {
  view: GraphView;
  onViewChange: (view: GraphView) => void;
  hasSelection: boolean;
  onFitGraph: () => void;
  onFitSelection: () => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
  onResetView: () => void;
}) {
  return (
    <CommandDock>
      {view === "canvas" ? (
        <div className="sg-graph-command-group" role="group" aria-label="Graph camera controls">
          <Tooltip label="Zoom in">
            <Button type="button" variant="quiet" onClick={onZoomIn} aria-label="Zoom in">
              +
            </Button>
          </Tooltip>
          <Tooltip label="Zoom out">
            <Button type="button" variant="quiet" onClick={onZoomOut} aria-label="Zoom out">
              −
            </Button>
          </Tooltip>
          <Tooltip label="Fit entire graph in view">
            <Button type="button" variant="secondary" onClick={onFitGraph}>
              Fit graph
            </Button>
          </Tooltip>
          <Tooltip label="Fit the current selection in view">
            <Button type="button" variant="secondary" disabled={!hasSelection} onClick={onFitSelection}>
              Fit selection
            </Button>
          </Tooltip>
          <Tooltip label="Reset pan and zoom">
            <Button type="button" variant="quiet" onClick={onResetView} aria-label="Reset view">
              Reset
            </Button>
          </Tooltip>
        </div>
      ) : null}
      <SegmentedControl
        label="Graph view mode"
        value={view}
        onChange={onViewChange}
        options={[
          { value: "canvas", label: "Canvas" },
          { value: "list", label: "Accessible list" },
        ]}
      />
    </CommandDock>
  );
}
