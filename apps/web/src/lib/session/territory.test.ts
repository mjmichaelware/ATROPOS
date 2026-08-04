/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import {
  emptyWorkspace,
  openProjectTab,
  closeProjectTab,
  focusProjectTab,
  activeTerritory,
  activeTabPermits,
} from './territory';

/**
 * SUP.UX.SESSION-TAB-DENSITY: "Switching sessions never mixes territory or
 * evidence." These assert that on the web surface, where tabs were previously
 * bare project ids carrying no territory at all.
 */
describe('territory-isolated tabs', () => {
  it('opening a tab focuses it and carries its territory', () => {
    const w = openProjectTab(emptyWorkspace(), 'p1', ['src/a']);
    expect(w.activeId).toBe('p1');
    expect([...activeTerritory(w)]).toEqual(['src/a']);
  });

  it('switching tabs never merges territory', () => {
    let w = openProjectTab(emptyWorkspace(), 'p1', ['src/a']);
    w = openProjectTab(w, 'p2', ['src/b']);

    expect([...activeTerritory(w)]).toEqual(['src/b']);
    w = focusProjectTab(w, 'p1');
    expect([...activeTerritory(w)]).toEqual(['src/a']);
    w = focusProjectTab(w, 'p2');
    expect([...activeTerritory(w)]).toEqual(['src/b']);
  });

  it('re-opening an already-open project focuses it rather than duplicating', () => {
    let w = openProjectTab(emptyWorkspace(), 'p1', ['src/a']);
    w = openProjectTab(w, 'p2', ['src/b']);
    w = openProjectTab(w, 'p1', ['src/a']);

    expect(w.sessions).toHaveLength(2);
    expect(w.activeId).toBe('p1');
  });

  it('an empty territory permits nothing — absence is not permission', () => {
    const w = openProjectTab(emptyWorkspace(), 'p1', []);
    expect(activeTabPermits(w, 'src/a')).toBe(false);
    expect(activeTabPermits(w, 'anything')).toBe(false);
  });

  it('permission follows the active tab, not the union of open tabs', () => {
    let w = openProjectTab(emptyWorkspace(), 'p1', ['src/a']);
    w = openProjectTab(w, 'p2', ['src/b']);

    expect(activeTabPermits(w, 'src/b/file.kt')).toBe(true);
    expect(activeTabPermits(w, 'src/a/file.kt')).toBe(false);
  });

  it('a path that merely shares a prefix string is refused', () => {
    const w = openProjectTab(emptyWorkspace(), 'p1', ['src/a']);
    expect(activeTabPermits(w, 'src/attack.kt')).toBe(false);
    expect(activeTabPermits(w, 'src/a')).toBe(true);
    expect(activeTabPermits(w, 'src/a/deep/file.kt')).toBe(true);
  });

  it('closing the active tab focuses a remaining one and adopts its territory', () => {
    let w = openProjectTab(emptyWorkspace(), 'p1', ['src/a']);
    w = openProjectTab(w, 'p2', ['src/b']);
    w = closeProjectTab(w, 'p2');

    expect(w.activeId).toBe('p1');
    expect([...activeTerritory(w)]).toEqual(['src/a']);
  });

  it('closing the last tab leaves no territory', () => {
    const w = closeProjectTab(openProjectTab(emptyWorkspace(), 'p1', ['src/a']), 'p1');
    expect(w.activeId).toBeNull();
    expect([...activeTerritory(w)]).toEqual([]);
  });

  it('focusing a tab that is not open refuses rather than inventing one', () => {
    const w = openProjectTab(emptyWorkspace(), 'p1', ['src/a']);
    expect(() => focusProjectTab(w, 'ghost')).toThrow(/not open/);
  });
});
