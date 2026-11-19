/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 12.1: Architectural Separation and Forbidden Runtime Root Enforcement.
 *
 * Canonical web ownership boundary recorded by the repository architecture reports.
 * Enforces strict boundary separation preventing the SpecGraph Foundry standalone website
 * from colliding with the local Atropos IDE web server. SpecGraph developer tools inside
 * the Atropos cockpit are routed exclusively under the developer tools namespace
 * (`/developer/specgraph`) while leaving the standalone Foundry site independently deployable.
 *
 * ## Research: Next.js Multi-Zone Deployment Architectures
 * Research Next.js multi-zone deployment architectures where separate web applications
 * (Atropos Cockpit and SpecGraph Foundry) share a unified domain via edge proxy routing
 * without code-level runtime coupling, asset bundle contamination, or shared server states.
 */
export const WebMergeArchitecture = {
  canonicalRoot: "apps/web",
  forbiddenRuntimeRoots: ["apps/specgraph-foundry/apps/web"] as const,
  developerToolsPrefix: "/developer/specgraph",

  isDeveloperToolsRoute(pathname: string): boolean {
    const normalized = pathname.trim();
    return (
      normalized === this.developerToolsPrefix ||
      normalized.startsWith(`${this.developerToolsPrefix}/`)
    );
  },

  isCanonicalRuntimeRoot(root: string): boolean {
    return root.trim() === this.canonicalRoot;
  },

  isForbiddenRuntimeRoot(root: string): boolean {
    const normalized = root.trim();
    return (this.forbiddenRuntimeRoots as readonly string[]).includes(normalized);
  },

  assertValidRuntimeRoute(pathname: string): void {
    if (this.isForbiddenRuntimeRoot(pathname)) {
      throw new Error(
        `Architectural violation: route '${pathname}' belongs to forbidden runtime root. Standalone SpecGraph must route via ${this.developerToolsPrefix}.`
      );
    }
  },

  resolveTenancyNamespace(pathname: string): "cockpit" | "specgraph_devtools" | "external" {
    if (this.isDeveloperToolsRoute(pathname)) {
      return "specgraph_devtools";
    }
    if (pathname.startsWith("/") && !this.isForbiddenRuntimeRoot(pathname)) {
      return "cockpit";
    }
    return "external";
  },
} as const;

export default WebMergeArchitecture;
