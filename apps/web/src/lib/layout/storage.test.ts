/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import {
  applyLayoutTheme,
  DEFAULT_LAYOUT,
  LAYOUT_THEMES,
  readLayoutTheme,
  toggleLayout,
  writeLayoutTheme,
} from './storage';

function memoryStorage(initial: Record<string, string> = {}) {
  const map = new Map(Object.entries(initial));
  return {
    getItem: (key: string) => map.get(key) ?? null,
    setItem: (key: string, value: string) => void map.set(key, value),
  };
}

describe('layout theme storage', () => {
  it('defaults to session when nothing is stored', () => {
    expect(readLayoutTheme(memoryStorage())).toBe('session');
    expect(DEFAULT_LAYOUT).toBe('session');
  });

  it('round-trips the workbench choice', () => {
    const storage = memoryStorage();
    writeLayoutTheme(storage, 'workbench');
    expect(readLayoutTheme(storage)).toBe('workbench');
  });

  it('falls back to default on unknown values instead of throwing', () => {
    expect(readLayoutTheme(memoryStorage({ 'atropos.layout': 'emacs' }))).toBe(
      'session'
    );
  });

  it('survives missing storage', () => {
    expect(readLayoutTheme(undefined)).toBe('session');
  });

  it('rejects invalid writes loudly', () => {
    const storage = memoryStorage();
    // @ts-expect-error deliberate invalid input
    expect(() => writeLayoutTheme(storage, 'tiling')).toThrow(/invalid layout/);
  });

  it('toggle is an involution across exactly two themes', () => {
    expect(toggleLayout('session')).toBe('workbench');
    expect(toggleLayout('workbench')).toBe('session');
    expect(LAYOUT_THEMES).toHaveLength(2);
  });

  it('applies as a data attribute so CSS keys off it', () => {
    const root = document.createElement('html');
    applyLayoutTheme(root, 'workbench');
    expect(root.dataset.layout).toBe('workbench');
  });
});
