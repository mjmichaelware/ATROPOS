/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import { SEEN_STORAGE_KEY, hasSeen, markSeen } from './client';

function memoryStorage(initial: Record<string, string> = {}): Storage {
  const map = new Map(Object.entries(initial));
  return {
    get length() {
      return map.size;
    },
    clear: () => map.clear(),
    getItem: (k: string) => map.get(k) ?? null,
    key: (i: number) => Array.from(map.keys())[i] ?? null,
    removeItem: (k: string) => void map.delete(k),
    setItem: (k: string, v: string) => void map.set(k, v),
  } as Storage;
}

function throwingStorage(): Storage {
  return {
    get length() {
      return 0;
    },
    clear: () => {
      throw new Error('denied');
    },
    getItem: () => {
      throw new Error('denied');
    },
    key: () => {
      throw new Error('denied');
    },
    removeItem: () => {
      throw new Error('denied');
    },
    setItem: () => {
      throw new Error('denied');
    },
  } as Storage;
}

describe('SUP.UX.FREE-PROVIDER-WELCOME seen-ness is content-addressed', () => {
  it('records the id, not a boolean', () => {
    const store = memoryStorage();
    markSeen('abc123', store);
    expect(store.getItem(SEEN_STORAGE_KEY)).toBe('abc123');
  });

  it('a changed welcome is unseen again', () => {
    // A boolean flag would suppress exactly the version they had not read.
    const store = memoryStorage({ [SEEN_STORAGE_KEY]: 'abc123' });
    expect(hasSeen('abc123', store)).toBe(true);
    expect(hasSeen('def456', store)).toBe(false);
  });

  it('nothing recorded means unseen', () => {
    expect(hasSeen('abc123', memoryStorage())).toBe(false);
  });
});

describe('storage failure fails toward showing the welcome', () => {
  it('an unreadable store reports unseen', () => {
    expect(hasSeen('abc123', throwingStorage())).toBe(false);
  });

  it('an unwritable store does not throw', () => {
    expect(() => markSeen('abc123', throwingStorage())).not.toThrow();
  });
});
