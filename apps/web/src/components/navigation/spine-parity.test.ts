/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import {
  HOE_A02_SPINE_ORDER,
  navigationSpine,
  developerToolsItem,
  engineStateGroup,
  globalRoutes,
} from './routes';

/**
 * HOE-A02 fixes the spine and its order; HOE-C07 keeps SpecGraph out of it.
 *
 * Four of the ten entries — Work, Conversations, Files, Agents — existed only
 * as `/projects/[id]/…` pages and appeared in no navigation, so they were
 * reachable only by typing a URL. This asserts the spine as shipped rather
 * than as intended.
 */
describe('HOE-A02 navigation spine', () => {
  it('carries all ten entries in the order the atom names', () => {
    expect(navigationSpine.map((item) => item.id)).toEqual([...HOE_A02_SPINE_ORDER]);
  });

  it('every spine entry has a distinct route', () => {
    const hrefs = navigationSpine.map((item) => item.href);
    expect(new Set(hrefs).size).toBe(hrefs.length);
  });

  it('every spine entry has a visible label', () => {
    navigationSpine.forEach((item) => {
      expect(item.label.trim().length).toBeGreaterThan(0);
    });
  });

  it('keeps SpecGraph out of the primary spine', () => {
    const spineHrefs = navigationSpine.map((item) => String(item.href));
    expect(spineHrefs.some((href) => href.includes('specgraph'))).toBe(false);
    expect(spineHrefs).not.toContain(String(globalRoutes.devTools));
  });

  it('mounts SpecGraph under Developer Tools', () => {
    expect(String(globalRoutes.specGraph).startsWith(String(globalRoutes.devTools))).toBe(true);
  });

  it('carries Developer Tools separately from the spine', () => {
    expect(navigationSpine.find((item) => item.id === developerToolsItem.id)).toBeUndefined();
    expect(developerToolsItem.href).toBe(globalRoutes.devTools);
  });
});

/**
 * Engine-state surfaces are reachable without joining the spine.
 *
 * Governance and Activity are real pages. Adding them to `navigationSpine`
 * would have made HOE-A02's ten-entry assertion above pass only by being
 * rewritten, so they are carried as their own group instead — visible, but not
 * counted as spine.
 */
describe('engine-state navigation group', () => {
  it('does not inflate the spine', () => {
    expect(navigationSpine).toHaveLength(HOE_A02_SPINE_ORDER.length);
    const spineIds = navigationSpine.map((item) => item.id);
    engineStateGroup.forEach((item) => {
      expect(spineIds).not.toContain(item.id);
    });
  });

  it('links both engine-state pages', () => {
    expect(engineStateGroup.map((item) => String(item.href))).toEqual([
      String(globalRoutes.governance),
      String(globalRoutes.activity),
    ]);
  });

  it('is not gated behind Developer Tools', () => {
    // What the system proposed about itself is not a developer concern.
    engineStateGroup.forEach((item) => {
      expect(String(item.href).startsWith(String(globalRoutes.devTools))).toBe(false);
    });
  });

  it('every entry has a visible label', () => {
    engineStateGroup.forEach((item) => {
      expect(item.label.trim().length).toBeGreaterThan(0);
    });
  });
});
