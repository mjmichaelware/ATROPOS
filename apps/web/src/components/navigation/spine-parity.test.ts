/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import {
  HOE_A02_SPINE_ORDER,
  navigationSpine,
  developerToolsItem,
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
