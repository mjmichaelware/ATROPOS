/**
 * ATOM 12.1: Architectural Separation and Forbidden Runtime Root Enforcement.
 *
 * Canonical web ownership boundary recorded by the repository architecture reports.
 * Enforces strict boundaries between standalone SpecGraph Foundry and the local Atropos cockpit,
 * ensuring embedded SpecGraph tools live exclusively under the developer tools namespace.
 *
 * ## Research: Next.js Multi-Zone Deployment Architectures
 * Research Next.js multi-zone deployment architectures where separate web applications
 * (Atropos Cockpit and SpecGraph Foundry) share a unified domain via edge proxy routing
 * without code-level runtime coupling or asset bundle contamination.
 */
export const WebMergeArchitecture = {
  canonicalRoot: 'apps/web',
  forbiddenRuntimeRoots: ['apps/specgraph-foundry/apps/web'] as const,
  developerToolsPrefix: '/developer/specgraph',
  isDeveloperToolsRoute(pathname: string): boolean {
    return pathname === this.developerToolsPrefix || pathname.startsWith(`${this.developerToolsPrefix}/`);
  },
  isCanonicalRuntimeRoot(root: string): boolean {
    return root === this.canonicalRoot;
  },
};
