"use client";

import { Background, BackgroundVariant, MiniMap, ReactFlow, applyNodeChanges, useReactFlow, type Node, type NodeChange } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { forwardRef, useCallback, useImperativeHandle, useMemo, useRef } from "react";
import type { LayoutPosition } from "@/lib/graph/schemas";
import type { RendererEdge, RendererNode } from "@/lib/graph/transform";
import { computeZoomTier, graphSizeTier } from "@/lib/graph/zoom";
import { GraphEdge, type SgFlowEdgeData } from "./graph-edge";
import { GraphNode, type SgFlowNodeData } from "./graph-node";

const NODE_TYPES = { sgNode: GraphNode };
const EDGE_TYPES = { sgEdge: GraphEdge };
const MARKER_CATEGORIES = ["authority-relation", "execution-dependency", "unknown"] as const;

export type GraphCanvasHandle = {
  fitGraph: () => void;
  fitSelection: () => void;
  zoomIn: () => void;
  zoomOut: () => void;
  resetView: () => void;
};

export const GraphCanvas = forwardRef<
  GraphCanvasHandle,
  {
    nodes: RendererNode[];
    edges: RendererEdge[];
    selectedId?: string;
    reducedMotion: boolean;
    onSelectNode: (id: string | undefined) => void;
    onSelectEdge: (id: string | undefined) => void;
    onNodeMoved: (id: string, position: LayoutPosition) => void;
    onZoomChange: (zoom: number) => void;
  }
>(function GraphCanvas({ nodes, edges, selectedId, reducedMotion, onSelectNode, onSelectEdge, onNodeMoved, onZoomChange }, ref) {
  const instance = useReactFlow();
  const zoomRef = useRef(1);
  const nodeCount = nodes.length;

  useImperativeHandle(
    ref,
    () => ({
      fitGraph: () => instance.fitView({ duration: reducedMotion ? 0 : 260 }),
      fitSelection: () => {
        const selected = instance.getNodes().filter((node) => node.selected);
        if (selected.length > 0) {
          instance.fitView({ nodes: selected, duration: reducedMotion ? 0 : 260, padding: 0.6 });
        }
      },
      zoomIn: () => instance.zoomIn({ duration: reducedMotion ? 0 : 160 }),
      zoomOut: () => instance.zoomOut({ duration: reducedMotion ? 0 : 160 }),
      resetView: () => instance.setViewport({ x: 0, y: 0, zoom: 1 }, { duration: reducedMotion ? 0 : 200 }),
    }),
    [instance, reducedMotion],
  );

  const flowNodes = useMemo<Node<SgFlowNodeData>[]>(() => {
    const tier = computeZoomTier(zoomRef.current, nodeCount);
    return nodes.map((node) => ({
      id: node.id,
      type: "sgNode",
      position: node.position,
      data: { ...node.data, tier, isSelected: node.id === selectedId },
      selected: node.id === selectedId,
    }));
  }, [nodes, nodeCount, selectedId]);

  const flowEdges = useMemo(
    () =>
      edges.map((edge) => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        type: "sgEdge",
        selected: edge.id === selectedId,
        data: { ...edge.data, showLabel: graphSizeTier(nodeCount) === "small" } satisfies SgFlowEdgeData,
      })),
    [edges, selectedId, nodeCount],
  );

  const handleNodesChange = useCallback(
    (changes: NodeChange<Node<SgFlowNodeData>>[]) => {
      for (const change of changes) {
        if (change.type === "position" && change.position && change.dragging === false) {
          onNodeMoved(change.id, change.position);
        }
      }
      applyNodeChanges(changes, flowNodes);
    },
    [flowNodes, onNodeMoved],
  );

  return (
    <div className="sg-graph-canvas" data-reduced-motion={reducedMotion || undefined}>
      <svg width="0" height="0" aria-hidden="true">
        <defs>
          {MARKER_CATEGORIES.map((category) => (
            <marker key={category} id={`sg-graph-arrow-${category}`} viewBox="0 0 10 10" refX={8} refY={5} markerWidth={7} markerHeight={7} orient="auto-start-reverse">
              <path d="M0,0 L10,5 L0,10 z" data-category={category} className="sg-graph-marker" />
            </marker>
          ))}
        </defs>
      </svg>
      <ReactFlow
        nodes={flowNodes}
        edges={flowEdges}
        nodeTypes={NODE_TYPES}
        edgeTypes={EDGE_TYPES}
        onNodesChange={handleNodesChange}
        onNodeClick={(_, node) => onSelectNode(node.id)}
        onEdgeClick={(_, edge) => onSelectEdge(edge.id)}
        onPaneClick={() => {
          onSelectNode(undefined);
          onSelectEdge(undefined);
        }}
        onMove={(_, viewport) => {
          zoomRef.current = viewport.zoom;
          onZoomChange(viewport.zoom);
        }}
        minZoom={0.05}
        maxZoom={2.5}
        fitView
        proOptions={{ hideAttribution: false }}
        elevateEdgesOnSelect
        nodesFocusable
        edgesFocusable
        aria-label="Graph canvas"
      >
        <Background variant={BackgroundVariant.Dots} gap={24} size={1} className="sg-graph-background" />
        <MiniMap className="sg-graph-minimap" pannable zoomable nodeColor={() => "var(--sg-graph-atom)"} aria-label="Graph overview" />
      </ReactFlow>
    </div>
  );
});
