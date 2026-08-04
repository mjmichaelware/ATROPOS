/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import {
  DEFAULT_THINKING_DEPTH,
  canCollapse,
  isAdditive,
  nextDepth,
  type ThinkingPresent,
} from './client';

const payload = (depth: number, hasMore: boolean, lineIds: string[]): ThinkingPresent => ({
  ok: true,
  present: true,
  nodeId: 'n-1',
  depth,
  depthLabel: 'x',
  hasMore,
  deepestAvailable: 3,
  levels: [
    { level: 1, label: 'Outline' },
    { level: 2, label: 'Reasoning' },
    { level: 3, label: 'Full trace' },
  ],
  lines: lineIds.map((id) => ({ id, minDepth: 1, text: id })),
});

describe('HOE-A08 / HOE-C06 depth is collapsed by default', () => {
  it('starts at the outline', () => {
    expect(DEFAULT_THINKING_DEPTH).toBe(1);
  });
});

describe('HOE-C08 the expand control exists only when it would do something', () => {
  it('offers the next depth when more is available', () => {
    expect(nextDepth(payload(1, true, ['a']))).toBe(2);
    expect(nextDepth(payload(2, true, ['a', 'b']))).toBe(3);
  });

  it('offers nothing when everything is shown', () => {
    // A drawer that opens onto nothing teaches the gesture means nothing.
    expect(nextDepth(payload(3, false, ['a', 'b', 'c']))).toBeNull();
  });

  it('never proposes a depth beyond the last one', () => {
    // hasMore is the engine's word; the client still refuses to ask for L4.
    expect(nextDepth(payload(3, true, ['a']))).toBeNull();
  });
});

describe('collapsing', () => {
  it('is offered only above the default', () => {
    expect(canCollapse(payload(1, true, ['a']))).toBe(false);
    expect(canCollapse(payload(2, true, ['a']))).toBe(true);
  });
});

describe('depth only ever adds', () => {
  it('accepts a deeper payload that kept every earlier line', () => {
    expect(isAdditive(payload(1, true, ['a']), payload(2, false, ['a', 'b']))).toBe(true);
  });

  it('rejects a deeper payload that dropped a line the operator was reading', () => {
    // The failure this exists to catch: disclosure that filters instead of
    // adding looks correct at every individual level.
    expect(isAdditive(payload(1, true, ['a']), payload(2, false, ['b', 'c']))).toBe(false);
  });
});
