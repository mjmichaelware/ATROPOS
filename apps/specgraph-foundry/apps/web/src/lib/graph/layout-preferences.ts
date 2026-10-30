import { createEmptyLayoutState, type LayoutAlgorithm, type VisualLayoutState } from "./schemas";
import { isSafeToPersist } from "./security";

type ReadableStorage = Pick<Storage, "getItem">;
type WritableStorage = Pick<Storage, "setItem">;

function storageKey(projectId: string, graphId: string) {
  return `sg-graph-layout:${projectId}:${graphId}`;
}

function isValidLayoutState(value: unknown): value is VisualLayoutState {
  return Boolean(
    value &&
      typeof value === "object" &&
      (value as VisualLayoutState).version === 1 &&
      typeof (value as VisualLayoutState).nodePositions === "object",
  );
}

/**
 * Client-local layout preference boundary. No server layout-persistence
 * endpoint exists for Group 14, so this is an explicit, honest
 * client-preference boundary rather than an invented server contract.
 */
export function loadLayoutPreference(storage: ReadableStorage, projectId: string, graphId: string, algorithm: LayoutAlgorithm): VisualLayoutState {
  try {
    const raw = storage.getItem(storageKey(projectId, graphId));
    if (!raw) return createEmptyLayoutState(algorithm);
    const parsed: unknown = JSON.parse(raw);
    return isValidLayoutState(parsed) ? parsed : createEmptyLayoutState(algorithm);
  } catch {
    return createEmptyLayoutState(algorithm);
  }
}

export function saveLayoutPreference(storage: WritableStorage, projectId: string, graphId: string, state: VisualLayoutState): boolean {
  const serialized = JSON.stringify(state);
  if (!isSafeToPersist(serialized)) {
    return false;
  }
  storage.setItem(storageKey(projectId, graphId), serialized);
  return true;
}
