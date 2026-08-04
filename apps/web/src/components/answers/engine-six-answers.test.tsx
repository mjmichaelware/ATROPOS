/* SPDX-License-Identifier: AGPL-3.0-only */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { SIX_ANSWER_KEYS } from '@atropos/web-contracts';
import { EngineSixAnswers } from './engine-six-answers';

const answer = (value: string) => ({ value, health: 'verified', signal: 'verified' });

const payload = {
  ok: true,
  answers: Object.fromEntries(SIX_ANSWER_KEYS.map((k) => [k, answer(`answer-${k}`)])),
  queue: { readable: true, queued: 0, failed: 0, running: [] },
  projectsReadable: true,
  provider: 'groq',
  heap: { usedMb: 1, maxMb: 2 },
};

function mockEngine(response: unknown, ok = true) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok,
      status: ok ? 200 : 503,
      json: async () => response,
    }),
  );
}

describe('EngineSixAnswers', () => {
  beforeEach(() => vi.useRealTimers());
  afterEach(() => vi.unstubAllGlobals());

  it('renders all six answers from the engine', async () => {
    mockEngine(payload);
    render(<EngineSixAnswers />);

    await waitFor(() => {
      SIX_ANSWER_KEYS.forEach((key) => {
        expect(screen.getByText(`answer-${key}`)).toBeTruthy();
      });
    });
  });

  it('shows the six questions, not just their answers', async () => {
    mockEngine(payload);
    render(<EngineSixAnswers />);

    await waitFor(() => {
      expect(screen.getByText(/What am I trying to accomplish/)).toBeTruthy();
      expect(screen.getByText(/Where is the evidence/)).toBeTruthy();
    });
  });

  it('carries a non-colour signal beside every answer', async () => {
    mockEngine(payload);
    render(<EngineSixAnswers />);

    await waitFor(() => {
      // §E: colour is never the only channel.
      expect(screen.getAllByText('verified').length).toBe(SIX_ANSWER_KEYS.length);
    });
  });

  it('reports an unreachable engine with a remedy instead of blank state', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('refused')));
    render(<EngineSixAnswers />);

    await waitFor(() => {
      expect(screen.getByText(/Engine not answering/)).toBeTruthy();
      expect(screen.getByText(/ATROPOS_BRIDGE_PORT/)).toBeTruthy();
    });
  });

  it('never invents an answer when the engine is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('refused')));
    render(<EngineSixAnswers />);

    await waitFor(() => expect(screen.getByText(/Engine not answering/)).toBeTruthy());
    // A placeholder that looks like an answer is indistinguishable from one.
    expect(screen.queryByText(/What am I trying to accomplish/)).toBeNull();
  });

  it('says an unreadable queue is a fault, not an empty queue', async () => {
    mockEngine({ ...payload, queue: { ...payload.queue, readable: false } });
    render(<EngineSixAnswers />);

    await waitFor(() => {
      expect(screen.getByText(/Queue unreadable/)).toBeTruthy();
    });
  });

  it('stays quiet about the queue when it is readable', async () => {
    mockEngine(payload);
    render(<EngineSixAnswers />);

    await waitFor(() => expect(screen.getByText('answer-objective')).toBeTruthy());
    expect(screen.queryByText(/Queue unreadable/)).toBeNull();
  });
});
