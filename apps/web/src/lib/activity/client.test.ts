/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import { byStage, hasBlockedStage, pipelineForSubject, type ActivityPayload } from './client';

const STAGES = ['plan', 'provider', 'tool', 'diff', 'test', 'verifier', 'artifact', 'deploy'];

const payload = (
  events: ActivityPayload['events'],
  missing: string[] = STAGES.filter((s) => !events.some((e) => e.stage === s))
): ActivityPayload => ({
  ok: true,
  stages: STAGES,
  missingStages: missing,
  everyStageReported: missing.length === 0,
  events,
});

const event = (stage: string, outcome = 'verified') => ({
  id: `${stage}-1`,
  at: '2026-01-01T00:00:00Z',
  stage,
  subject: `node-${stage}`,
  outcome,
  detail: '',
});

describe('C3-P19 the monitor shows every stage', () => {
  it('keeps a row for a stage that produced nothing', () => {
    const rows = byStage(payload([event('plan')]));
    expect(rows).toHaveLength(STAGES.length);
    expect(rows.find((r) => r.stage === 'test')?.missing).toBe(true);
    expect(rows.find((r) => r.stage === 'test')?.events).toEqual([]);
  });

  it('attaches each event to its own stage', () => {
    const rows = byStage(payload([event('plan'), event('test')]));
    expect(rows.find((r) => r.stage === 'plan')?.events).toHaveLength(1);
    expect(rows.find((r) => r.stage === 'deploy')?.missing).toBe(true);
  });
});

describe('coverage is not health', () => {
  it('a fully-covered but fully-blocked run still reports blocked', () => {
    const all = payload(STAGES.map((s) => event(s, 'blocked')), []);
    expect(all.everyStageReported).toBe(true);
    expect(hasBlockedStage(all)).toBe(true);
  });

  it('one blocked stage among many is enough to surface', () => {
    expect(hasBlockedStage(payload([event('plan'), event('test', 'blocked')]))).toBe(true);
  });

  it('a clean stream reports nothing blocked', () => {
    expect(hasBlockedStage(payload([event('plan')]))).toBe(false);
  });

  it('produces How? only for a matching recorded subject', () => {
    const matching = payload([{ ...event('plan'), subject: 'work-42' }, { ...event('test'), subject: 'work-42' }]);
    expect(pipelineForSubject(matching, 'work-42')).toContain('Stages: plan, test');
    expect(pipelineForSubject(matching, 'unrelated-work')).toBeUndefined();
  });
});
