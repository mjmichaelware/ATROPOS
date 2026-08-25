/* SPDX-License-Identifier: AGPL-3.0-only */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mostRecentFirst, readSessions } from './client';

afterEach(() => vi.unstubAllGlobals());

describe('F-WEB-002 sessions client', () => {
  it('reads the list from the engine payload', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          count: 1,
          sessions: [
            {
              id: 's1',
              title: 'T',
              turnCount: 2,
              createdAt: '2026-01-01T00:00:00Z',
              updatedAt: '2026-01-01T01:00:00Z',
            },
          ],
        }),
      }),
    );
    const result = await readSessions();
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.data[0].title).toBe('T');
  });

  it('most recent update sorts first', () => {
    const sorted = mostRecentFirst([
      { id: 'a', title: 'A', turnCount: 0, createdAt: 't', updatedAt: '2026-01-02T00:00:00Z' },
      { id: 'b', title: 'B', turnCount: 9, createdAt: 't', updatedAt: '2026-01-09T00:00:00Z' },
    ]);
    expect(sorted.map((session) => session.id)).toEqual(['b', 'a']);
  });

  it('surfaces unreachable as a fault, never as an empty list', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('down')));
    const result = await readSessions();
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.reason).toBe('bridge-unreachable');
  });
});
