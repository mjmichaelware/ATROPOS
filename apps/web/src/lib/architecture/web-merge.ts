/** Canonical web ownership boundary recorded by the repository architecture reports. */
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
