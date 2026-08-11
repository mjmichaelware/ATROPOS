import type { GraphMode, LayoutAlgorithm } from "./schemas";
import { isSafeIdentifier } from "./security";

export type GraphView = "canvas" | "list";

export type FindingFilterParam = "all" | "ERROR" | "WARNING" | "INFO";

export type GraphUrlState = {
  mode: GraphMode;
  algorithm: LayoutAlgorithm;
  query: string;
  category: string;
  status: string;
  selected: string | undefined;
  view: GraphView;
  plan: string | undefined;
  findingFilter: FindingFilterParam;
};

const LAYOUT_ALGORITHMS: LayoutAlgorithm[] = ["blueprint", "compact", "freeform", "focus"];
const FINDING_FILTERS: FindingFilterParam[] = ["all", "ERROR", "WARNING", "INFO"];
const MAX_QUERY_LENGTH = 200;

export const DEFAULT_GRAPH_URL_STATE: GraphUrlState = {
  mode: "authority",
  algorithm: "blueprint",
  query: "",
  category: "all",
  status: "all",
  selected: undefined,
  view: "canvas",
  plan: undefined,
  findingFilter: "all",
};

/**
 * Parses shareable graph URL state. Every field is bounded and validated;
 * malformed or oversized values fall back to safe defaults rather than
 * propagating untrusted input.
 */
export function parseGraphUrlState(params: URLSearchParams): GraphUrlState {
  const layoutParam = params.get("layout") ?? "";
  const selectedParam = params.get("selected") ?? "";
  const planParam = params.get("plan") ?? "";
  const findingFilterParam = params.get("finding") ?? "all";
  return {
    mode: params.get("mode") === "execution" ? "execution" : "authority",
    algorithm: (LAYOUT_ALGORITHMS as string[]).includes(layoutParam) ? (layoutParam as LayoutAlgorithm) : "blueprint",
    query: (params.get("q") ?? "").slice(0, MAX_QUERY_LENGTH),
    category: (params.get("category") ?? "all").slice(0, 64),
    status: (params.get("status") ?? "all").slice(0, 64),
    selected: isSafeIdentifier(selectedParam) ? selectedParam : undefined,
    view: params.get("view") === "list" ? "list" : "canvas",
    plan: isSafeIdentifier(planParam) ? planParam : undefined,
    findingFilter: (FINDING_FILTERS as string[]).includes(findingFilterParam) ? (findingFilterParam as FindingFilterParam) : "all",
  };
}

export function serializeGraphUrlState(state: GraphUrlState): URLSearchParams {
  const params = new URLSearchParams();
  if (state.mode !== DEFAULT_GRAPH_URL_STATE.mode) params.set("mode", state.mode);
  if (state.algorithm !== DEFAULT_GRAPH_URL_STATE.algorithm) params.set("layout", state.algorithm);
  if (state.query) params.set("q", state.query.slice(0, MAX_QUERY_LENGTH));
  if (state.category !== "all") params.set("category", state.category);
  if (state.status !== "all") params.set("status", state.status);
  if (state.selected && isSafeIdentifier(state.selected)) params.set("selected", state.selected);
  if (state.view !== DEFAULT_GRAPH_URL_STATE.view) params.set("view", state.view);
  if (state.plan && isSafeIdentifier(state.plan)) params.set("plan", state.plan);
  if (state.findingFilter !== "all") params.set("finding", state.findingFilter);
  return params;
}
