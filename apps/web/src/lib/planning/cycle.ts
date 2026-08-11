import type { AuthorityRelation } from "./schemas";

export type ProposedRelation = {
  from_atom_id: string;
  to_atom_id: string;
  relation_type: string;
};

export type CycleCheckResult =
  | { kind: "not-applicable"; reason: string }
  | { kind: "no-cycle-in-loaded-subset" }
  | { kind: "cycle-detected"; path: string[] };

const MAX_TRAVERSAL_NODES = 20_000;

/**
 * Builds an adjacency map of only REQUIRES relations from the loaded,
 * possibly-partial relation set. Malformed or unknown-typed records are
 * skipped rather than crashing.
 */
export function buildRequiresSubgraph(relations: AuthorityRelation[]): Map<string, Set<string>> {
  const adjacency = new Map<string, Set<string>>();
  for (const relation of relations) {
    if (!relation || relation.relation_type !== "REQUIRES") continue;
    if (typeof relation.from_atom_id !== "string" || typeof relation.to_atom_id !== "string") continue;
    if (!relation.from_atom_id || !relation.to_atom_id) continue;
    if (!adjacency.has(relation.from_atom_id)) adjacency.set(relation.from_atom_id, new Set());
    adjacency.get(relation.from_atom_id)!.add(relation.to_atom_id);
  }
  return adjacency;
}

/**
 * Bounded, iterative breadth-first path search (no recursion, so no
 * recursion-depth hazard on large or deeply-chained loaded subsets).
 */
function findPath(adjacency: Map<string, Set<string>>, start: string, target: string): string[] | undefined {
  if (start === target) return [start];
  const visited = new Set<string>([start]);
  const queue: string[] = [start];
  const parent = new Map<string, string>();
  let visitedCount = 0;
  while (queue.length > 0) {
    if (visitedCount > MAX_TRAVERSAL_NODES) return undefined;
    const current = queue.shift() as string;
    visitedCount += 1;
    const neighbors = adjacency.get(current);
    if (!neighbors) continue;
    for (const next of neighbors) {
      if (visited.has(next)) continue;
      visited.add(next);
      parent.set(next, current);
      if (next === target) {
        const path = [target];
        let node = target;
        while (node !== start) {
          node = parent.get(node) as string;
          path.push(node);
        }
        return path.reverse();
      }
      queue.push(next);
    }
  }
  return undefined;
}

/**
 * Advisory-only cycle check over the currently loaded relation subset.
 *
 * Only REQUIRES relations contribute to execution ordering; other relation
 * types are never treated as cycle-invalid here. A "no-cycle-in-loaded-subset"
 * result is never presented as proof the full project graph is acyclic — the
 * server remains the sole authority for that determination.
 */
export function checkProposedRelationCycle(relations: AuthorityRelation[], proposed: ProposedRelation): CycleCheckResult {
  if (proposed.relation_type !== "REQUIRES") {
    return {
      kind: "not-applicable",
      reason: `${proposed.relation_type} relations do not contribute to execution ordering; a cycle in this relation type is not treated as invalid.`,
    };
  }
  if (!proposed.from_atom_id || !proposed.to_atom_id || proposed.from_atom_id === proposed.to_atom_id) {
    return { kind: "no-cycle-in-loaded-subset" };
  }
  const adjacency = buildRequiresSubgraph(relations);
  const existingPathBack = findPath(adjacency, proposed.to_atom_id, proposed.from_atom_id);
  if (!existingPathBack) {
    return { kind: "no-cycle-in-loaded-subset" };
  }
  return { kind: "cycle-detected", path: [proposed.from_atom_id, ...existingPathBack] };
}
