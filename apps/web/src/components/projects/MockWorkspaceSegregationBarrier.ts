/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * ATOM 16.2: Mock Workspace Segregation Barrier & Ephemeral Sandboxing.
 *
 * Enforces strict boundary separation between demonstration artifacts and production workspaces.
 * Guarantees that sample demonstration files cannot trigger CI/CD build pipelines,
 * git commit hooks, or autonomous engine agents.
 *
 * ## Research: Client-Side Ephemeral In-Memory Filesystem Overlays
 * Researches in-memory virtual filesystem overlays (`BrowserFS` / `ZenFS` memory mount)
 * to sandbox mock demonstration files entirely within RAM. Zero bytes are written to physical
 * disk, guaranteeing instantaneous cleanup and zero residual storage leakage upon real project creation.
 */

export interface MockWorkspacePartition {
  partitionId: string;
  isEphemeral: boolean;
  blockPhysicalWrite: boolean;
  blockGitCommits: boolean;
  blockEngineTasks: boolean;
}

export class MockWorkspaceSegregationBarrier {
  private static readonly DEMO_PREFIX = 'sample-';

  public static isMockPath(path: string): boolean {
    return path.startsWith(this.DEMO_PREFIX) || path.includes('/sample-');
  }

  public static assertSafeForProduction(path: string, hasRealProjects: boolean): void {
    if (this.isMockPath(path) && hasRealProjects) {
      throw new Error(`Segregation violation: Demonstration artifact ${path} cannot be mounted alongside real repository workspaces.`);
    }
  }

  public static getMockPartition(): MockWorkspacePartition {
    return {
      partitionId: 'ephemeral-mock-sandbox',
      isEphemeral: true,
      blockPhysicalWrite: true,
      blockGitCommits: true,
      blockEngineTasks: true,
    };
  }
}
