/**
 * True project id extraction from a pathname, used only for active-state
 * matching in navigation chrome — never for building a route (use the
 * builders in routes.ts for that).
 */
export function projectIdFromPathname(pathname: string): string | undefined {
  // Both trees are project-scoped: ATROPOS at /projects/[id] and the SpecGraph
  // subsystem at /developer/specgraph/[projectId].
  const match =
    /^\/projects\/([^/]+)/.exec(pathname) ??
    /^\/developer\/specgraph\/([^/]+)/.exec(pathname);
  const id = match?.[1];
  return id && id !== "new" ? id : undefined;
}
