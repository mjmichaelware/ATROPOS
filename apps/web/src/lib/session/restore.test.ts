/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import { restoreSession } from './restore';

const defaults = {
  activeProjectId: null as string | null,
  openTabs: [] as string[],
  simpleModeEnabled: true,
  informationLevel: 2,
};

/**
 * HOE-A09 pairs persistence with a recovery report and HOE-E06 forbids a
 * silent resume, so every case here asserts the report as well as the state.
 */
describe('session restore', () => {
  it('reports a fresh start when nothing is stored', () => {
    const { session, report } = restoreSession(null, defaults);
    expect(session).toEqual(defaults);
    expect(report.restored).toBe(false);
    expect(report.clean).toBe(true);
  });

  it('restores a complete session cleanly', () => {
    const stored = JSON.stringify({
      activeProjectId: 'p1',
      openTabs: ['p1', 'p2'],
      simpleModeEnabled: false,
      informationLevel: 4,
    });
    const { session, report } = restoreSession(stored, defaults);
    expect(session.activeProjectId).toBe('p1');
    expect(session.openTabs).toEqual(['p1', 'p2']);
    expect(session.informationLevel).toBe(4);
    expect(report.clean).toBe(true);
    expect(report.restored).toBe(true);
  });

  it('reports unreadable storage instead of failing silently', () => {
    const { session, report } = restoreSession('{not json', defaults);
    expect(session).toEqual(defaults);
    expect(report.clean).toBe(false);
    expect(report.message).toMatch(/could not be read/);
  });

  it('refuses a payload that is not an object', () => {
    for (const raw of ['[]', '"nope"', '42']) {
      const { session, report } = restoreSession(raw, defaults);
      expect(session).toEqual(defaults);
      expect(report.clean).toBe(false);
    }
  });

  it('drops a field whose stored type no longer matches and names it', () => {
    // A stored openTabs that arrived as a string would otherwise reach
    // components typed to expect an array.
    const stored = JSON.stringify({ ...defaults, openTabs: 'p1', informationLevel: 3 });
    const { session, report } = restoreSession(stored, defaults);
    expect(session.openTabs).toEqual([]);
    expect(session.informationLevel).toBe(3);
    expect(report.restored).toBe(true);
    expect(report.clean).toBe(false);
    expect(report.dropped).toBe(1);
    expect(report.message).toMatch(/openTabs/);
  });

  it('drops a null where a value was expected', () => {
    const stored = JSON.stringify({ ...defaults, simpleModeEnabled: null });
    const { session, report } = restoreSession(stored, defaults);
    expect(session.simpleModeEnabled).toBe(true);
    expect(report.dropped).toBe(1);
  });

  it('ignores unknown keys rather than trusting them into state', () => {
    const stored = JSON.stringify({ ...defaults, injectedByALaterBuild: 'x' });
    const { session } = restoreSession(stored, defaults);
    expect('injectedByALaterBuild' in session).toBe(false);
  });

  it('does not mutate the defaults it was given', () => {
    const original = { ...defaults };
    restoreSession(JSON.stringify({ activeProjectId: 'p9' }), defaults);
    expect(defaults).toEqual(original);
  });
});
