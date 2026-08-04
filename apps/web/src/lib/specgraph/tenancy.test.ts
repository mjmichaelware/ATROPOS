/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import {
  CARRIED_OVER,
  SPECGRAPH_TENANCY_PREFIX,
  isSpecGraphRoute,
  tenancyFindings,
  tenantedWorkspaces,
} from './tenancy';
import { navigationSpine, specGraphSections } from '@/components/navigation/routes';

describe('§1.3/§12.2 SpecGraph is a subsystem, not the identity', () => {
  it('holds no place in the primary spine', () => {
    expect(tenancyFindings()).toEqual([]);
  });

  it('the spine is ATROPOS work, not compiler workspaces', () => {
    const spineIds = navigationSpine.map((item) => item.id);
    expect(spineIds).toContain('projects');
    expect(spineIds).not.toContain('sources');
    expect(spineIds).not.toContain('graph');
    expect(spineIds).not.toContain('handoff');
  });
});

describe('re-tenanting is placement, not deletion', () => {
  it('every workspace is retained', () => {
    expect(tenantedWorkspaces('p1')).toHaveLength(specGraphSections.length);
    expect(specGraphSections.length).toBeGreaterThan(0);
  });

  it('every workspace now lives under Developer Tools', () => {
    const outside = tenantedWorkspaces('p1').filter((w) => !w.underDeveloperTools);
    expect(outside).toEqual([]);
  });

  it('sources, research, graph, handoff and routing all survive the move', () => {
    const ids = tenantedWorkspaces('p1').map((w) => w.id);
    ['sources', 'research', 'graph', 'handoff', 'routing'].forEach((id) => {
      expect(ids).toContain(id);
    });
  });

  it('recognises its own routes', () => {
    expect(isSpecGraphRoute(SPECGRAPH_TENANCY_PREFIX)).toBe(true);
    expect(isSpecGraphRoute(`${SPECGRAPH_TENANCY_PREFIX}/p1/graph`)).toBe(true);
    expect(isSpecGraphRoute('/projects/p1/work')).toBe(false);
  });
});

describe('what carried over is behaviour, not visuals', () => {
  it('every carried-over property names its owner', () => {
    expect(CARRIED_OVER.length).toBeGreaterThan(0);
    CARRIED_OVER.forEach((entry) => {
      expect(entry.owner).toMatch(/\.(ts|tsx|kt)$/);
    });
  });

  it('carries no styling artifact', () => {
    // The move preserved the trust properties. The visual language was not
    // the thing worth keeping.
    CARRIED_OVER.forEach((entry) => {
      expect(entry.owner).not.toMatch(/\.css$/);
    });
  });
});
