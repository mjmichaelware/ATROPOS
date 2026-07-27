/**
 * True project id extraction from a pathname, used only for active-state
 * matching in navigation chrome — never for building a route (use the
 * builders in routes.ts for that).
 */
export function projectIdFromPathname(pathname: string): string | undefined {
  const match = /^\/projects\/([^/]+)/.exec(pathname);
  const id = match?.[1];
  return id && id !== "new" ? id : undefined;
}
