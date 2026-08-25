/* SPDX-License-Identifier: AGPL-3.0-only */
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  INTERRUPT_GAPS,
  cancelWork,
  runningWork,
  freezeQueue,
  resumeQueue,
  getFreezeStatus,
  hardInterrupt,
} from './client';

afterEach(() => vi.unstubAllGlobals());

describe('ADD-W-003 work-queue client', () => {
  it('reads running work from the six-answers payload', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          ok: true,
          answers: {},
          queue: { readable: true, queued: 0, failed: 0, running: [{ id: 'w1', title: 'Build docs' }] },
        }),
      }),
    );
    const result = await runningWork();
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.data[0].id).toBe('w1');
  });

  it('attributes a soft interrupt to this surface', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ok: true }),
    });
    vi.stubGlobal('fetch', fetchMock);
    await cancelWork('w1');
    const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(body.id).toBe('w1');
    expect(body.decidedBy).toBe('web-cockpit');
  });

  it('attributes a hard interrupt to this surface', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ok: true, id: 'w1', terminal: true }),
    });
    vi.stubGlobal('fetch', fetchMock);
    await hardInterrupt('w1');
    const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(body.id).toBe('w1');
    expect(body.decidedBy).toBe('web-cockpit');
  });

  it('freezes the queue with attribution', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ok: true, frozen: true, changed: true }),
    });
    vi.stubGlobal('fetch', fetchMock);
    await freezeQueue();
    const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(body.decidedBy).toBe('web-cockpit');
  });

  it('resumes the queue with attribution', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ok: true, frozen: false, changed: true }),
    });
    vi.stubGlobal('fetch', fetchMock);
    await resumeQueue();
    const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(body.decidedBy).toBe('web-cockpit');
  });

  it('gets the freeze status', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ok: true, frozen: true }),
    });
    vi.stubGlobal('fetch', fetchMock);
    const result = await getFreezeStatus();
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.data.frozen).toBe(true);
  });

  it('reports the bridge unreachable without throwing', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network down')));
    const result = await cancelWork('w1');
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.reason).toBe('bridge-unreachable');
  });

  it('no longer reports any interrupt gaps since all verbs are now served', () => {
    expect(INTERRUPT_GAPS.length).toBe(0);
  });
});