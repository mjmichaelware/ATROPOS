/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import { activitySignal, progressTokenFrom } from './activity-signal';
import {
  defaultChannels,
  levelFor,
  restoreChannels,
  setSurfaceLevel,
} from '../disclosure/surface-channel';

describe('HOE-E03 animation bound to real progress', () => {
  const obs = (runningCount: number, progressToken: string) => ({ runningCount, progressToken });

  it('does not animate when nothing is running', () => {
    expect(activitySignal(null, obs(0, 'x'), false)).toEqual({ animate: false, reason: 'idle' });
  });

  it('animates when running work advances', () => {
    const signal = activitySignal(obs(1, 'a'), obs(1, 'b'), false);
    expect(signal.animate).toBe(true);
    expect(signal.reason).toBe('progressing');
  });

  it('does NOT animate a run that is stuck at the same checkpoint', () => {
    const signal = activitySignal(obs(1, 'a'), obs(1, 'a'), false);
    expect(signal.animate).toBe(false);
    // The situation the operator most needs to notice must not look busy.
    expect(signal.reason).toBe('running-without-progress');
  });

  it('animates on a first observation of running work', () => {
    expect(activitySignal(null, obs(2, 'a'), false).animate).toBe(true);
  });

  it('reduced motion wins over every progress state', () => {
    expect(activitySignal(obs(1, 'a'), obs(1, 'b'), true)).toEqual({
      animate: false,
      reason: 'reduced-motion',
    });
  });

  it('the progress token is not time-derived', () => {
    const running = [{ id: 'j1', state: 'working', detail: 'step', attempt: 1 }];
    expect(progressTokenFrom(running)).toBe(progressTokenFrom(running));
  });

  it('the token changes when a node actually advances', () => {
    const before = progressTokenFrom([{ id: 'j1', state: 'working', detail: 'a', attempt: 1 }]);
    const after = progressTokenFrom([{ id: 'j1', state: 'working', detail: 'b', attempt: 1 }]);
    expect(before).not.toBe(after);
  });

  it('the token is order independent', () => {
    const a = { id: 'a', state: 's', detail: 'd', attempt: null };
    const b = { id: 'b', state: 's', detail: 'd', attempt: null };
    expect(progressTokenFrom([a, b])).toBe(progressTokenFrom([b, a]));
  });
});

describe('HOE-E04 independent surface verbosity', () => {
  it('setting one surface leaves the others untouched', () => {
    const channels = setSurfaceLevel(defaultChannels(), 'web', 4);
    expect(levelFor(channels, 'web')).toBe(4);
    expect(levelFor(channels, 'cli')).toBe(2);
    expect(levelFor(channels, 'android')).toBe(2);
  });

  it('expanding on web never expands the terminal', () => {
    let channels = setSurfaceLevel(defaultChannels(), 'cli', 1);
    channels = setSurfaceLevel(channels, 'web', 4);
    expect(levelFor(channels, 'cli')).toBe(1);
  });

  it('each surface coerces its own stored level independently', () => {
    const channels = restoreChannels({ web: 3, cli: 'nonsense', android: 99 });
    expect(levelFor(channels, 'web')).toBe(3);
    expect(levelFor(channels, 'cli')).toBe(2);
    expect(levelFor(channels, 'android')).toBe(2);
  });

  it('a non-object payload restores every surface to the default', () => {
    expect(restoreChannels(null)).toEqual(defaultChannels());
    expect(restoreChannels('x')).toEqual(defaultChannels());
  });
});
