/* SPDX-License-Identifier: AGPL-3.0-only */

import {
  HOE_A02_SPINE_ORDER,
  globalRoutes,
  navigationSpine,
  specGraphSections,
  specGraphProjectRoute,
} from '@/components/navigation/routes';

/**
 * SpecGraph's tenancy inside ATROPOS.
 *
 * §1.3 and §12.2: SpecGraph is an engine inside ATROPOS, not the application's
 * identity. It used to *be* the project navigation, which made a compiler
 * subsystem the primary information architecture — every operator arriving at
 * the product was shown Sources / Research / Graph / Handoff before they were
 * shown their own work.
 *
 * The re-tenanting is a placement decision, not a deletion. Every SpecGraph
 * workspace is retained in full under Developer Tools, which §2.10 hides by
 * default. What carries over is the part worth keeping: the discipline that a
 * claim shows its evidence and a gap shows as a gap. What does not carry over
 * is its claim on the front door.
 *
 * This module holds the rule as assertable data. A route table is the kind of
 * thing that drifts back one convenient link at a time, and a comment in
 * `routes.ts` cannot fail a test.
 */

/** The prefix every SpecGraph route must sit behind. */
export const SPECGRAPH_TENANCY_PREFIX = '/developer/specgraph';

export interface TenancyFinding {
  readonly id: string;
  readonly detail: string;
}

/**
 * Checks that SpecGraph occupies no place in the primary spine.
 *
 * Three separate ways it could reclaim the front door, all checked: an entry in
 * the spine, a spine id naming one of its workspaces, or a global route that
 * points into its tree without going through Developer Tools.
 */
export function tenancyFindings(): readonly TenancyFinding[] {
  const findings: TenancyFinding[] = [];

  const spineHref = navigationSpine.find((item) =>
    String(item.href).startsWith(SPECGRAPH_TENANCY_PREFIX),
  );
  if (spineHref) {
    findings.push({
      id: spineHref.id,
      detail: `"${spineHref.id}" puts a SpecGraph route in the primary spine.`,
    });
  }

  const workspaceIds = new Set(specGraphSections.map((section) => section.id));
  // "overview" is a generic word SpecGraph happens to use; the ids that would
  // actually signal a takeover are its named workspaces.
  workspaceIds.delete('overview');
  for (const id of HOE_A02_SPINE_ORDER) {
    if (workspaceIds.has(id)) {
      findings.push({ id, detail: `The spine names SpecGraph's "${id}" workspace.` });
    }
  }

  for (const [name, route] of Object.entries(globalRoutes)) {
    const href = String(route);
    if (href.startsWith(SPECGRAPH_TENANCY_PREFIX) && !href.startsWith('/developer')) {
      findings.push({ id: name, detail: `globalRoutes.${name} escapes Developer Tools.` });
    }
  }

  return findings;
}

/** Every SpecGraph workspace, with the route it now lives at. */
export function tenantedWorkspaces(projectId: string): ReadonlyArray<{
  id: string;
  label: string;
  href: string;
  underDeveloperTools: boolean;
}> {
  return specGraphSections.map((section) => {
    const href = String(section.build(projectId));
    return {
      id: section.id,
      label: section.label,
      href,
      underDeveloperTools: href.startsWith(SPECGRAPH_TENANCY_PREFIX),
    };
  });
}

/**
 * What survived the move.
 *
 * Named explicitly because "we kept SpecGraph" and "we kept SpecGraph's visual
 * language" are different statements, and only the first is true. These are
 * behaviours, each already owned elsewhere in this repository — listing them
 * here does not reimplement them, it records which ones the re-tenanting was
 * required to preserve.
 */
export const CARRIED_OVER: readonly { id: string; owner: string }[] = [
  { id: 'evidence-behind-every-claim', owner: 'apps/web/src/lib/evidence/affordance.ts' },
  { id: 'progressive-disclosure-only-adds', owner: 'apps/web/src/lib/disclosure/levels.ts' },
  { id: 'gap-renders-as-gap', owner: 'apps/web/src/lib/activity/client.ts' },
  { id: 'territory-visible-as-weight', owner: 'apps/web/src/lib/design/territory-material.ts' },
];

export function isSpecGraphRoute(pathname: string): boolean {
  return pathname === SPECGRAPH_TENANCY_PREFIX || pathname.startsWith(`${SPECGRAPH_TENANCY_PREFIX}/`);
}

export { specGraphProjectRoute };
