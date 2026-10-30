"use client";

import { useSyncExternalStore, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/status-badge";
import { Sheet } from "@/components/ui/sheet";
import { connectedRelationshipCount } from "@/lib/graph/search";
import type { RendererEdge, RendererGraphContent, RendererNode } from "@/lib/graph/transform";

const DESKTOP_QUERY = "(min-width: 768px)";

function subscribeDesktopViewport(onChange: () => void) {
  if (typeof window === "undefined" || !("matchMedia" in window)) return () => {};
  const media = window.matchMedia(DESKTOP_QUERY);
  media.addEventListener("change", onChange);
  return () => media.removeEventListener("change", onChange);
}

function readDesktopViewport() {
  return typeof window !== "undefined" && "matchMedia" in window ? window.matchMedia(DESKTOP_QUERY).matches : true;
}

function useIsDesktopViewport() {
  return useSyncExternalStore(subscribeDesktopViewport, readDesktopViewport, () => true);
}

function InspectorBody({
  node,
  edge,
  content,
  onClose,
  nodeExtra,
  edgeExtra,
}: {
  node?: RendererNode;
  edge?: RendererEdge;
  content: RendererGraphContent;
  onClose: () => void;
  nodeExtra?: ReactNode;
  edgeExtra?: ReactNode;
}) {
  if (node) {
    const relationships = connectedRelationshipCount(node.id, content.edges);
    return (
      <div className="sg-graph-inspector-body">
        <header>
          <p className="sg-micro-label">{node.data.typeLabel}</p>
          <h2>{node.data.label}</h2>
          {node.data.status ? <StatusBadge tone="info" label={node.data.status} /> : null}
        </header>
        <dl>
          <div>
            <dt>Category</dt>
            <dd>{node.data.category}</dd>
          </div>
          <div>
            <dt>Connected relationships</dt>
            <dd>{relationships}</dd>
          </div>
          <div>
            <dt>Identifier</dt>
            <dd className="sg-mono">{node.data.sourceId}</dd>
          </div>
        </dl>
        {nodeExtra}
        <Button type="button" variant="secondary" onClick={onClose}>
          Close inspector
        </Button>
      </div>
    );
  }
  if (edge) {
    return (
      <div className="sg-graph-inspector-body">
        <header>
          <p className="sg-micro-label">{edge.data.category === "unknown" ? "Relationship" : edge.data.category.replace("-", " ")}</p>
          <h2>{edge.data.relationLabel ?? "Unlabeled relationship"}</h2>
        </header>
        <dl>
          <div>
            <dt>From</dt>
            <dd className="sg-mono">{edge.source}</dd>
          </div>
          <div>
            <dt>To</dt>
            <dd className="sg-mono">{edge.target}</dd>
          </div>
          {edge.data.rationale ? (
            <div>
              <dt>Rationale</dt>
              <dd>{edge.data.rationale}</dd>
            </div>
          ) : null}
          {typeof edge.data.confidence === "number" ? (
            <div>
              <dt>Confidence</dt>
              <dd>{Math.round(edge.data.confidence * 100)}%</dd>
            </div>
          ) : null}
          {typeof edge.data.inferred === "boolean" ? (
            <div>
              <dt>Inferred</dt>
              <dd>{edge.data.inferred ? "Yes" : "No"}</dd>
            </div>
          ) : null}
        </dl>
        {edgeExtra}
        <Button type="button" variant="secondary" onClick={onClose}>
          Close inspector
        </Button>
      </div>
    );
  }
  return (
    <div className="sg-graph-inspector-body sg-graph-inspector-empty">
      <p>Select a node or edge to inspect it here.</p>
    </div>
  );
}

export function GraphInspector({
  selectedNode,
  selectedEdge,
  content,
  onClose,
  nodeExtra,
  edgeExtra,
}: {
  selectedNode?: RendererNode;
  selectedEdge?: RendererEdge;
  content: RendererGraphContent;
  onClose: () => void;
  nodeExtra?: ReactNode;
  edgeExtra?: ReactNode;
}) {
  const desktop = useIsDesktopViewport();
  const hasSelection = Boolean(selectedNode || selectedEdge);
  const body: ReactNode = <InspectorBody node={selectedNode} edge={selectedEdge} content={content} onClose={onClose} nodeExtra={nodeExtra} edgeExtra={edgeExtra} />;

  if (desktop) {
    return (
      <aside className="sg-graph-inspector" aria-label="Graph inspector">
        {body}
      </aside>
    );
  }

  return (
    <Sheet open={hasSelection} onOpenChange={(open) => !open && onClose()} title="Graph inspector">
      {body}
    </Sheet>
  );
}
