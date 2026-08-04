/* SPDX-License-Identifier: AGPL-3.0-only */
import { describe, expect, it } from 'vitest';
import { evidenceAffordance, explainActions, openableEvidence } from './affordance';

const ref = (id: string, location: string | null = `.atropos/evidence/${id}`) => ({
  id,
  kind: 'gate',
  location,
});

describe('HOE-A05 / P20-G06 evidence gates the claim', () => {
  it('a verified claim with evidence renders as verified and can expand', () => {
    const a = evidenceAffordance({ state: 'verified', evidence: [ref('e1')] });
    expect(a.renderedState).toBe('verified');
    expect(a.canExpand).toBe(true);
    expect(a.downgradeReason).toBeNull();
  });

  it('a verified claim with NO evidence is refused, not decorated', () => {
    const a = evidenceAffordance({ state: 'verified', evidence: [] });
    expect(a.renderedState).not.toBe('verified');
    expect(a.downgradeReason).toMatch(/cites no evidence/);
  });

  it('the downgrade target is unproven, not failed', () => {
    // Reporting an unproven claim as a failure is fabrication in the other
    // direction.
    const a = evidenceAffordance({ state: 'verified', evidence: [] });
    expect(a.renderedState).toBe('tested');
    expect(a.renderedState).not.toBe('blocked');
  });

  it('a lesser claim without evidence is left alone', () => {
    ['implemented', 'compiled', 'tested', 'blocked'].forEach((state) => {
      const a = evidenceAffordance({ state, evidence: [] });
      expect(a.renderedState).toBe(state);
      expect(a.downgradeReason).toBeNull();
    });
  });
});

describe('HOE-C08 motion is bound to real state', () => {
  it('does not morph when there is nothing to reveal', () => {
    expect(evidenceAffordance({ state: 'tested', evidence: [] }).morph).toBe(false);
  });

  it('morphs only when evidence exists', () => {
    expect(evidenceAffordance({ state: 'tested', evidence: [ref('e1')] }).morph).toBe(true);
  });
});

describe('HOE-A06 why / how / evidence actions', () => {
  it('offers all three when all three have a source', () => {
    const actions = explainActions({ why: 'because', how: 'like this', evidence: [ref('e1')] });
    expect(actions.map((a) => a.id)).toEqual(['why', 'how', 'evidence']);
  });

  it('omits an action rather than rendering it disabled', () => {
    // A greyed-out Evidence button still asserts evidence exists somewhere.
    const actions = explainActions({ why: 'because', how: null, evidence: [] });
    expect(actions.map((a) => a.id)).toEqual(['why']);
  });

  it('treats blank text as absent', () => {
    expect(explainActions({ why: '   ', how: '', evidence: [] })).toEqual([]);
  });
});

describe('openable evidence', () => {
  it('keeps only evidence that can actually be opened', () => {
    const refs = [ref('a'), ref('b', null), ref('c', '  ')];
    expect(openableEvidence(refs).map((r) => r.id)).toEqual(['a']);
  });
});
