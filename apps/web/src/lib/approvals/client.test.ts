/* SPDX-License-Identifier: AGPL-3.0-only */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { decideApproval, readPendingApprovals, sortForDecision } from './client';
import type { ApprovalCard } from '@atropos/web-contracts';

const card = (over: Partial<ApprovalCard> = {}): ApprovalCard => ({
  id: 'ap-1',
  proposalId: 'p-1',
  actor: 'patch:x',
  operation: 'WRITE_FILE',
  territory: ['src/**'],
  reason: 'outside grant',
  requestedAt: '2026-01-02T00:00:00Z',
  pending: true,
  ...over,
});

function mockFetchOnce(payload: unknown, ok = true) {
  const fn = vi.fn().mockResolvedValue({
    ok,
    status: ok ? 200 : 500,
    json: async () => payload,
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('F-WEB-008 bridge approvals client', () => {
  it('reads pending cards through the contract guard', async () => {
    mockFetchOnce({ ok: true, pending: [card()] });
    const result = await readPendingApprovals();
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.data).toHaveLength(1);
      expect(result.data[0].operation).toBe('WRITE_FILE');
    }
  });

  it('drops malformed rows instead of rendering them as decisions', async () => {
    mockFetchOnce({
      ok: true,
      pending: [card(), { id: 'broken' }, 'not-even-an-object'],
    });
    const result = await readPendingApprovals();
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.data).toHaveLength(1);
  });

  it('reports the bridge unreachable without throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new TypeError('network down')),
    );
    const result = await readPendingApprovals();
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.reason).toBe('bridge-unreachable');
  });

  it('decides with attribution to this surface', async () => {
    const fetchMock = mockFetchOnce({ ok: true, id: 'ap-1', approved: true });
    const result = await decideApproval('ap-1', true);
    expect(result.ok).toBe(true);
    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(String(init?.body));
    expect(body.decidedBy).toBe('web-cockpit');
    expect(body.approved).toBe(true);
  });

  it('sorts oldest-requested first and drops settled rows', () => {
    const sorted = sortForDecision([
      card({ id: 'new', requestedAt: '2026-01-05T00:00:00Z' }),
      card({ id: 'old', requestedAt: '2026-01-01T00:00:00Z' }),
      card({ id: 'settled', pending: false, requestedAt: '2025-01-01T00:00:00Z' }),
    ]);
    expect(sorted.map((c) => c.id)).toEqual(['old', 'new']);
  });
});
