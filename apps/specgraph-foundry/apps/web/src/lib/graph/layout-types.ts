import type { LayoutAlgorithm, LayoutPosition } from "./schemas";

export type LayoutDirection = "DOWN" | "RIGHT";

export type LayoutOptions = {
  algorithm: LayoutAlgorithm;
  direction: LayoutDirection;
  nodeSpacing: number;
  layerSpacing: number;
};

export type LayoutInputNode = {
  id: string;
  width: number;
  height: number;
};

export type LayoutInputEdge = {
  id: string;
  source: string;
  target: string;
};

/**
 * Deterministic, normalized worker input. Building this from renderer
 * content is a pure function (see layout-normalize.ts): stable sort by ID,
 * fixed dimensions per node, and option normalization ensure identical
 * (nodes, edges, options) always produce the same request payload.
 */
export type LayoutWorkerRequest = {
  generation: number;
  algorithm: LayoutAlgorithm;
  options: LayoutOptions;
  nodes: LayoutInputNode[];
  edges: LayoutInputEdge[];
};

export type LayoutWorkerSuccess = {
  generation: number;
  ok: true;
  positions: Record<string, LayoutPosition>;
};

export type LayoutWorkerFailure = {
  generation: number;
  ok: false;
  error: string;
};

export type LayoutWorkerResponse = LayoutWorkerSuccess | LayoutWorkerFailure;

export const DEFAULT_NODE_WIDTH = 220;
export const DEFAULT_NODE_HEIGHT = 72;
